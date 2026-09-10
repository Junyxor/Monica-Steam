/*
 * Motion model adapted from KernelSU FloatingBottomBar and the BiliPai
 * KernelSU-aligned Dock implementation. KernelSU is GPL-3.0; Monica Steam is
 * distributed under the same license.
 */
package takagi.ru.monica.steam.navigation.liquidglass.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal data class LiquidGlassDockDragSpec(
    val baseResistance: Float = 1.02f,
    val overscrollResistance: Float = 0.34f,
    val overscrollLimitItems: Float = 0.5f,
    val flingProjectionTimeSeconds: Float = 0.20f,
    val maxReleaseStepCount: Int = 1
)

internal fun resolveLiquidGlassDockVelocityItemsPerSecond(
    velocityPxPerSecond: Float,
    itemWidthPx: Float
): Float = if (itemWidthPx > 0f) velocityPxPerSecond / itemWidthPx else 0f

internal fun resolveLiquidGlassDockReleaseTargetIndex(
    currentValue: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec()
): Int {
    if (itemCount <= 0) return 0
    val projectedValue = currentValue +
        resolveLiquidGlassDockVelocityItemsPerSecond(
            velocityPxPerSecond = velocityPxPerSecond,
            itemWidthPx = itemWidthPx
        ) * dragSpec.flingProjectionTimeSeconds
    var targetIndex = projectedValue.roundToInt()
    val baseIndex = currentValue.roundToInt()
    val maxStep = dragSpec.maxReleaseStepCount.coerceAtLeast(1)
    if (abs(targetIndex - baseIndex) > maxStep) {
        targetIndex = baseIndex + (targetIndex - baseIndex).sign * maxStep
    }
    return targetIndex.coerceIn(0, itemCount - 1)
}

internal class LiquidGlassDockMotionState internal constructor(
    initialIndex: Int,
    private val itemCount: Int,
    private val scope: CoroutineScope,
    private val onIndexChanged: (Int) -> Unit,
    reduceMotion: Boolean,
    private val dragSpec: LiquidGlassDockDragSpec = LiquidGlassDockDragSpec()
) {
    private val reducedMotionAnimationSpec: AnimationSpec<Float> =
        tween<Float>(durationMillis = REDUCED_MOTION_DURATION_MILLIS)
    private val valueAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val velocityAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.5f, 300f, 0.01f)
    }
    private val pressProgressAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 1000f, 0.001f)
    }
    private val scaleXAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.6f, 250f, 0.001f)
    }
    private val scaleYAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(0.7f, 250f, 0.001f)
    }
    private val offsetSnapAnimationSpec = if (reduceMotion) {
        reducedMotionAnimationSpec
    } else {
        spring(1f, 300f, 0.5f)
    }

    private val valueAnimation = Animatable(initialIndex.toFloat(), 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val offsetAnimation = Animatable(0f)
    private val mutatorMutex = MutatorMutex()

    private var motionGeneration = 0
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var releaseJob: Job? = null
    private var offsetJob: Job? = null
    private var desiredValue = initialIndex.toFloat()
    private var desiredOffset = 0f
    private var pointerPressed = false
    private var isSettlingDrag = false

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val deformationVelocityItemsPerSecond: Float get() = velocityAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val dragOffset: Float get() = offsetAnimation.value
    val isRunning: Boolean get() = valueAnimation.isRunning

    // This is a pointer-input mailbox consumed by the frame-coalesced drag job,
    // not observable UI state. Keeping it outside SnapshotState avoids a snapshot
    // write for every high-frequency touch sample.
    var velocityPxPerSecond: Float = 0f
        private set

    var isDragging by mutableStateOf(false)
        private set

    var targetIndex by mutableIntStateOf(initialIndex)
        private set

    private fun startNewMotion(): Int {
        motionGeneration += 1
        isSettlingDrag = false
        return motionGeneration
    }

    private fun press() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(KERNEL_SU_PRESSED_SCALE, scaleYAnimationSpec) }
        }
    }

    /**
     * Tap feedback should be brief. Keeping pressProgress at 1 until the pill has
     * completely travelled forces the expensive lens, chromatic-aberration and
     * inner-shadow layers to redraw while the destination page is cold-composed.
     */
    private fun releasePressVisuals() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    /** Drag release keeps the deformation alive until the pill is nearly settled. */
    private fun releaseAfterSettled(onSettled: (() -> Unit)? = null) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = ((itemCount - 1).toFloat() * 0.025f).coerceAtLeast(0.001f)
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            onSettled?.invoke()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    private suspend fun updateDeformationVelocity(
        gestureVelocityPxPerSecond: Float,
        itemWidthPx: Float
    ) {
        val valueRange = (itemCount - 1).toFloat().coerceAtLeast(1f)
        val targetVelocity = resolveLiquidGlassDockVelocityItemsPerSecond(
            velocityPxPerSecond = gestureVelocityPxPerSecond,
            itemWidthPx = itemWidthPx
        ) / valueRange
        // The gesture layer already owns a VelocityTracker. Reusing its result
        // avoids a second tracker + calculateVelocity() pass on every pointer event.
        velocityAnimation.snapTo(targetVelocity)
    }

    private fun animateToValue(value: Float, onSettled: (() -> Unit)? = null) {
        scope.launch {
            mutatorMutex.mutate {
                // Pointer press and drag-start already own the glass deformation.
                // Re-pressing here cancels/restarts three Animatables exactly when
                // a cold destination page begins composing, which causes tap jank.
                val nextTarget = value.fastCoerceIn(0f, (itemCount - 1).toFloat())
                targetIndex = nextTarget.roundToInt().coerceIn(0, itemCount - 1)
                valueJob?.cancel()
                valueJob = launch { valueAnimation.animateTo(nextTarget, valueAnimationSpec) }
                if (deformationVelocityItemsPerSecond != 0f) {
                    velocityJob?.cancel()
                    velocityJob = launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                if (onSettled == null) {
                    releasePressVisuals()
                } else {
                    releaseAfterSettled(onSettled)
                }
            }
        }
    }

    fun onDrag(
        dragAmountPx: Float,
        itemWidthPx: Float,
        gestureVelocityPxPerSecond: Float = 0f
    ) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        if (!isDragging) {
            isDragging = true
            startNewMotion()
            valueJob?.cancel()
            valueJob = null
            offsetJob?.cancel()
            velocityJob?.cancel()
            desiredValue = valueAnimation.value
            desiredOffset = offsetAnimation.value
            velocityPxPerSecond = 0f
            // A normal pointer drag has already received setPressed(true) from
            // the input target. Only synthesize press feedback for callers that
            // begin a drag without that interaction signal.
            if (!pointerPressed) press()
        }
        velocityPxPerSecond = gestureVelocityPxPerSecond

        val isOverscrolling = desiredValue < 0f || desiredValue > (itemCount - 1).toFloat()
        val resistance = if (isOverscrolling) {
            dragSpec.overscrollResistance
        } else {
            dragSpec.baseResistance
        }
        desiredValue = (desiredValue + dragAmountPx / itemWidthPx * resistance).fastCoerceIn(
            -dragSpec.overscrollLimitItems,
            (itemCount - 1).toFloat() + dragSpec.overscrollLimitItems
        )
        desiredOffset += dragAmountPx

        // Pointer input can arrive much faster than display frames. The old path
        // cancelled and relaunched a coroutine for every event, only to snap the
        // same Animatables again. Keep the freshest desired state and commit it at
        // most once per frame instead.
        if (valueJob?.isActive == true) return
        valueJob = scope.launch {
            awaitFrame()
            val latestValue = desiredValue.fastCoerceIn(0f, (itemCount - 1).toFloat())
            val latestOffset = desiredOffset
            val latestVelocityPxPerSecond = velocityPxPerSecond
            valueAnimation.snapTo(latestValue)
            offsetAnimation.snapTo(latestOffset)
            updateDeformationVelocity(
                gestureVelocityPxPerSecond = latestVelocityPxPerSecond,
                itemWidthPx = itemWidthPx
            )
        }
    }

    fun setPressed(pressed: Boolean) {
        if (pressed) {
            if (pointerPressed) return
            pointerPressed = true
            if (isSettlingDrag) startNewMotion()
            press()
        } else {
            pointerPressed = false
            // InteractionSource emits Release around the same time as onDragEnd.
            // Do not let that release cancel releaseAfterSettled(), otherwise the
            // drag can visually settle without dispatching onIndexChanged().
            if (!isDragging && !isSettlingDrag) {
                releasePressVisuals()
            }
        }
    }

    fun onDragEnd(velocityX: Float, itemWidthPx: Float) {
        if (itemWidthPx <= 0f || itemCount <= 0) return
        valueJob?.cancel()
        valueJob = null
        isDragging = false
        isSettlingDrag = true
        val generation = motionGeneration
        velocityPxPerSecond = velocityX
        val releaseTargetIndex = resolveLiquidGlassDockReleaseTargetIndex(
            currentValue = desiredValue,
            velocityPxPerSecond = velocityX,
            itemWidthPx = itemWidthPx,
            itemCount = itemCount,
            dragSpec = dragSpec
        )
        targetIndex = releaseTargetIndex
        desiredValue = releaseTargetIndex.toFloat()
        desiredOffset = 0f
        animateToValue(releaseTargetIndex.toFloat()) {
            isSettlingDrag = false
            if (generation == motionGeneration) {
                velocityPxPerSecond = 0f
                onIndexChanged(releaseTargetIndex)
            }
        }
        offsetJob?.cancel()
        offsetJob = scope.launch {
            offsetAnimation.animateTo(0f, offsetSnapAnimationSpec)
        }
    }

    fun updateIndex(index: Int) {
        if (isDragging || itemCount <= 0) return
        val safeIndex = index.coerceIn(0, itemCount - 1)
        if (
            safeIndex == targetIndex &&
            (
                isRunning ||
                    abs(value - safeIndex.toFloat()) < 0.005f ||
                    abs(targetValue - safeIndex.toFloat()) < 0.005f
                )
        ) {
            return
        }
        startNewMotion()
        targetIndex = safeIndex
        desiredValue = safeIndex.toFloat()
        velocityPxPerSecond = 0f
        animateToValue(safeIndex.toFloat())
    }
}

private const val KERNEL_SU_PRESSED_SCALE = 78f / 56f
private const val REDUCED_MOTION_DURATION_MILLIS = 120

@Composable
internal fun rememberLiquidGlassDockMotionState(
    initialIndex: Int,
    itemCount: Int,
    reduceMotion: Boolean,
    onIndexChanged: (Int) -> Unit
): LiquidGlassDockMotionState {
    val scope = rememberCoroutineScope()
    val currentOnIndexChanged by rememberUpdatedState(onIndexChanged)
    return remember(itemCount, reduceMotion) {
        LiquidGlassDockMotionState(
            initialIndex = initialIndex,
            itemCount = itemCount,
            scope = scope,
            onIndexChanged = { currentOnIndexChanged(it) },
            reduceMotion = reduceMotion
        )
    }
}

internal fun Modifier.liquidGlassDockHorizontalDrag(
    motionState: LiquidGlassDockMotionState,
    itemWidthPx: Float
): Modifier = pointerInput(motionState, itemWidthPx) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false)
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            motionState.onDrag(over, itemWidthPx)
        }

        if (dragStart != null) {
            velocityTracker.addPosition(dragStart.uptimeMillis, dragStart.position)
            var cancelled = false
            try {
                horizontalDrag(dragStart.id) { change ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val dragAmount = change.position.x - change.previousPosition.x
                    motionState.onDrag(
                        dragAmountPx = dragAmount,
                        itemWidthPx = itemWidthPx,
                        gestureVelocityPxPerSecond = velocityTracker.calculateVelocity().x
                    )
                }
            } catch (_: Exception) {
                cancelled = true
            }
            motionState.onDragEnd(
                velocityX = if (cancelled) 0f else velocityTracker.calculateVelocity().x,
                itemWidthPx = itemWidthPx
            )
        }
    }
}
