package com.example.dinorush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    interface Listener {
        fun onHudUpdate(score: Int, best: Int, distanceMeters: Int, coins: Int, powerUpLabel: String?, powerUpFraction: Float)
        fun onGameOver(score: Int, best: Int, distanceMeters: Int, coins: Int, isNewBest: Boolean)
    }
    var listener: Listener? = null
    var soundManager: SoundManager? = null
    enum class State { IDLE, RUNNING, PAUSED, GAME_OVER }
    var state = State.IDLE
        private set
    fun isPlaying() = state == State.RUNNING
    private val prefs = GamePrefs(context)

    private enum class ObstacleType(val isAir: Boolean) { SMALL_ROCK(false), LARGE_ROCK(false), CACTUS(false), LOG(false), BARRIER(false), FLYER(true), DRONE(true) }
    private enum class PowerUpType(val label: String) { SHIELD("SHIELD"), MAGNET("MAGNET"), SLOW_MO("SLOW-MO"), DOUBLE_SCORE("2x SCORE"), INVINCIBLE("INVINCIBLE"), DOUBLE_JUMP("DOUBLE JUMP") }
    private data class Obstacle(val type: ObstacleType, var x: Float, val y: Float, val w: Float, val h: Float, val baseY: Float = y, var wobble: Float = 0f, var passed: Boolean = false)
    private data class Coin(var x: Float, var y: Float, var collected: Boolean = false)
    private data class PowerUpEntity(var x: Float, var y: Float, val type: PowerUpType, var collected: Boolean = false)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Int, val radius: Float)

    private var viewW = 0f
    private var viewH = 0f
    private var groundY = 0f
    private var scale = 1f
    private var dinoX = 0f
    private var dinoY = 0f
    private var dinoW = 0f
    private var dinoStandH = 0f
    private var dinoDuckH = 0f
    private var dinoH = 0f
    private var velocityY = 0f
    private var onGround = true
    private var isDucking = false
    private var isHoldingJump = false
    private var jumpHoldElapsed = 0f
    private var doubleJumpCharges = 0
    private var doubleJumpUsedThisAirtime = false
    private var coyoteTimer = 0f
    private var jumpBufferTimer = 0f
    private var runPhase = 0f
    private var wasAirborne = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var swipeConsumedAsDuck = false
    private var touchDownTimeNanos = 0L
    private var particleTrailTimer = 0f
    private var distanceTraveled = 0f
    private var speed = 0f
    private var baseSpeed = 0f
    private var scoreF = 0f
    private var score = 0
    private var coinsThisRun = 0
    private var jumpsThisRun = 0
    private var obstaclesPassedThisRun = 0
    private var combo = 0
    private var bestComboThisRun = 0
    private var comboTimer = 0f
    private var screenShakeTimer = 0f
    private var screenShakeStrength = 0f
    private var obstacleSpawnDistanceRemaining = 0f
    private var distanceSinceLastCoinRow = 0f
    private var nextCoinRowGap = 0f
    private var distanceSinceLastPowerUp = 0f
    private var nextPowerUpGap = 0f
    private val obstacles = ArrayList<Obstacle>()
    private val coins = ArrayList<Coin>()
    private val powerUps = ArrayList<PowerUpEntity>()
    private val particles = ArrayList<Particle>()
    private var shieldActive = false
    private var shieldTimer = 0f
    private var invincibleTimer = 0f
    private var slowMoTimer = 0f
    private var doubleScoreTimer = 0f
    private var activePowerUpLabel: String? = null
    private var activePowerUpMaxDuration = 1f
    private var activePowerUpTimeLeft = 0f
    private var dayNightPhase = 0f
    private var environmentIndex = 0
    private val environmentDistancePeriod = 2600f
    private var hudUpdateTimer = 0f

    private val gravityRef = 3600f
    private val jumpVelocityRef = -1380f
    private val doubleJumpVelocityRef = -1180f
    private val holdGravityScale = 0.42f
    private val maxHoldSeconds = 0.22f
    private val coyoteDuration = 0.12f
    private val jumpBufferDuration = 0.10f
    private val comboGracePeriod = 2.6f
    private val pixelsPerMeter = 40f
    private val dayNightPeriodSeconds = 90f

    private val skyPaint = Paint()
    private val groundPaint = Paint().apply { color = Color.parseColor("#3B2A2A") }
    private val groundLinePaint = Paint().apply { color = Color.parseColor("#5A4038"); strokeWidth = 4f }
    private val hillPaint = Paint().apply { alpha = 120 }
    private val obstaclePaint = Paint().apply { style = Paint.Style.FILL }
    private val obstacleOutline = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#22000000") }
    private val dinoBodyPaint = Paint().apply { color = Color.parseColor("#3FA687") }
    private val dinoBellyPaint = Paint().apply { color = Color.parseColor("#E8F5EF") }
    private val dinoEyePaint = Paint().apply { color = Color.parseColor("#12241E") }
    private val coinPaint = Paint().apply { color = Color.parseColor("#F4C430") }
    private val coinShinePaint = Paint().apply { color = Color.parseColor("#FFF6D5") }
    private val powerUpTextPaint = Paint().apply { color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val shieldRingPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#6FD8FF") }
    private val particlePaint = Paint()
    private val rectF = RectF()
    private val dinoHitbox = RectF()
    private val otherHitbox = RectF()

    init { isClickable = false; isFocusable = false }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w.toFloat(); viewH = h.toFloat(); groundY = viewH * 0.80f; scale = viewH / 720f
        dinoW = viewH * 0.16f; dinoStandH = viewH * 0.20f; dinoDuckH = dinoStandH * 0.55f
        dinoH = dinoStandH; dinoX = viewW * 0.14f; dinoY = groundY - dinoH; onGround = true
    }

    private val choreographer = Choreographer.getInstance()
    private var lastFrameNanos = 0L
    private var loopPosted = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow || state != State.RUNNING) {
                loopPosted = false
                lastFrameNanos = 0L
                return
            }
            if (lastFrameNanos != 0L) {
                var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                if (dt > 0.05f) dt = 0.05f
                update(dt)
            }
            lastFrameNanos = frameTimeNanos
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    private fun startRenderLoop() {
        if (!isAttachedToWindow || state != State.RUNNING || loopPosted) return
        loopPosted = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopRenderLoop() {
        if (loopPosted) choreographer.removeFrameCallback(frameCallback)
        loopPosted = false
        lastFrameNanos = 0L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRenderLoop()
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        super.onDetachedFromWindow()
    }

    fun startNewGame() {
        obstacles.clear(); coins.clear(); powerUps.clear(); particles.clear()
        distanceTraveled = 0f; baseSpeed = 340f * scale; speed = baseSpeed; scoreF = 0f; score = 0
        coinsThisRun = 0; jumpsThisRun = 0; obstaclesPassedThisRun = 0
        dinoY = groundY - dinoStandH; dinoH = dinoStandH; velocityY = 0f; onGround = true
        isDucking = false; isHoldingJump = false; doubleJumpCharges = 1; doubleJumpUsedThisAirtime = false
        coyoteTimer = 0f; jumpBufferTimer = 0f
        combo = 0; bestComboThisRun = 0; comboTimer = 0f; screenShakeTimer = 0f; screenShakeStrength = 0f
        shieldActive = false; shieldTimer = 0f; invincibleTimer = 0f; slowMoTimer = 0f; doubleScoreTimer = 0f; activePowerUpLabel = null
        activePowerUpMaxDuration = 1f; activePowerUpTimeLeft = 0f
        dayNightPhase = 0f; environmentIndex = 0
        obstacleSpawnDistanceRemaining = baseSpeed * 12f
        distanceSinceLastCoinRow = 0f; nextCoinRowGap = baseSpeed * 6f
        distanceSinceLastPowerUp = 0f; nextPowerUpGap = baseSpeed * 16f
        state = State.RUNNING
        lastFrameNanos = 0L
        startRenderLoop()
    }
    fun pauseGame() {
        if (state == State.RUNNING) {
            state = State.PAUSED
            stopRenderLoop()
        }
    }
    fun resumeGame() {
        if (state == State.PAUSED) {
            state = State.RUNNING
            startRenderLoop()
        }
    }
    fun stopToMenu() {
        state = State.IDLE
        stopRenderLoop()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == State.GAME_OVER && event.actionMasked == MotionEvent.ACTION_DOWN) {
            startNewGame()
            return true
        }
        if (state != State.RUNNING) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownTimeNanos = event.eventTime * 1_000_000L
                swipeConsumedAsDuck = false
                tryJump()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - touchDownY
                val dx = event.x - touchDownX
                val elapsedSec = ((event.eventTime * 1_000_000L) - touchDownTimeNanos).coerceAtLeast(1L) / 1_000_000_000f
                val downwardVelocity = dy / elapsedSec
                val deliberateSwipe = dy > 60f * scale && dy > kotlin.math.abs(dx) * 1.15f &&
                    elapsedSec <= 0.30f && downwardVelocity > 650f * scale
                if (!swipeConsumedAsDuck && deliberateSwipe && !onGround) {
                    swipeConsumedAsDuck = true
                    if (onGround || dinoY > groundY - dinoStandH * 0.4f) { velocityY = 0f; dinoY = groundY - dinoDuckH; onGround = true }
                    isDucking = true; isHoldingJump = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDucking = false
                isHoldingJump = false
                swipeConsumedAsDuck = false
            }
        }
        return true
    }
    fun performJump() { if (state == State.RUNNING) tryJump() }
    fun setDuckingExternal(active: Boolean) {
        if (state != State.RUNNING) return
        if (active) {
            if (onGround || dinoY > groundY - dinoStandH * 0.4f) { velocityY = 0f; dinoY = groundY - dinoDuckH; onGround = true }
            isDucking = true; isHoldingJump = false
        } else isDucking = false
    }
    private fun tryJump() {
        if ((onGround || coyoteTimer > 0f) && !isDucking) {
            velocityY = jumpVelocityRef * scale; onGround = false; isHoldingJump = true; jumpHoldElapsed = 0f
            coyoteTimer = 0f; jumpBufferTimer = 0f; doubleJumpUsedThisAirtime = false; jumpsThisRun++; soundManager?.playJump()
        } else if (!onGround && doubleJumpCharges > 0 && !doubleJumpUsedThisAirtime) {
            velocityY = doubleJumpVelocityRef * scale; doubleJumpCharges--; doubleJumpUsedThisAirtime = true
            isHoldingJump = true; jumpHoldElapsed = 0f; jumpsThisRun++
            spawnBurst(dinoX + dinoW / 2f, dinoY + dinoH / 2f, Color.parseColor("#8FE3FF"), 10); soundManager?.playJump()
        } else {
            jumpBufferTimer = jumpBufferDuration
        }
    }

    private fun update(dt: Float) {
        val slowFactor = if (slowMoTimer > 0f) 0.55f else 1f
        val rampedSpeed = baseSpeed + min(baseSpeed * 1.6f, distanceTraveled * 0.02f)
        speed = rampedSpeed * slowFactor
        distanceTraveled += speed * dt
        val multiplier = if (doubleScoreTimer > 0f) 2f else 1f
        scoreF += speed * dt * 0.05f * multiplier; score = scoreF.toInt()
        updateDinoPhysics(dt); updateEnvironment(dt); updateSpawning(dt); updateObstacles(dt); updateCoins(dt); updatePowerUps(dt); updateEffects(dt); updateParticles(dt)
        comboTimer = max(0f, comboTimer - dt)
        if (comboTimer <= 0f && combo > 0) combo = 0
        if (prefs.graphicsQuality >= 2) {
            particleTrailTimer += dt
            if (particleTrailTimer >= 0.08f && onGround) {
                particleTrailTimer = 0f
                spawnBurst(dinoX + dinoW * 0.12f, groundY - 4f * scale, Color.WHITE, 2)
            }
        } else {
            particleTrailTimer = 0f
        }
        hudUpdateTimer += dt
        if (hudUpdateTimer >= 0.1f) {
            hudUpdateTimer = 0f
            listener?.onHudUpdate(score, max(prefs.bestScore, score), (distanceTraveled / pixelsPerMeter).toInt(), coinsThisRun, activePowerUpLabel, if (activePowerUpMaxDuration > 0f) activePowerUpTimeLeft / activePowerUpMaxDuration else 0f)
        }
    }
    private fun updateDinoPhysics(dt: Float) {
        if (isHoldingJump) jumpHoldElapsed += dt
        if (!onGround) coyoteTimer = max(0f, coyoteTimer - dt)
        if (jumpBufferTimer > 0f) {
            jumpBufferTimer = max(0f, jumpBufferTimer - dt)
            if (onGround && !isDucking) tryJump()
        }
        val holding = isHoldingJump && velocityY < 0f && jumpHoldElapsed < maxHoldSeconds
        velocityY += gravityRef * scale * (if (holding) holdGravityScale else 1f) * dt
        dinoY += velocityY * dt
        dinoH = if (isDucking && onGround) dinoDuckH else dinoStandH
        val floor = groundY - dinoH
        if (dinoY >= floor) {
            dinoY = floor
            if (!onGround && wasAirborne) soundManager?.playLand()
            onGround = true; coyoteTimer = coyoteDuration; velocityY = 0f
            isHoldingJump = false; doubleJumpCharges = 1; doubleJumpUsedThisAirtime = false; wasAirborne = false
        } else { onGround = false; wasAirborne = true }
        runPhase += dt * (if (onGround) 10f else 4f)
    }
    private fun updateEnvironment(dt: Float) {
        dayNightPhase = (dayNightPhase + dt / dayNightPeriodSeconds) % 1f
        environmentIndex = ((distanceTraveled / environmentDistancePeriod).toInt()) % ENV_COUNT
    }
    private fun currentDifficultyTier(): Int = when { score < 120 -> 0; score < 300 -> 1; score < 600 -> 2; else -> 3 }
    private fun updateSpawning(dt: Float) {
        val moveDelta = speed * dt
        obstacleSpawnDistanceRemaining -= moveDelta
        distanceSinceLastCoinRow += moveDelta
        distanceSinceLastPowerUp += moveDelta
        if (obstacleSpawnDistanceRemaining <= 0f) {
            spawnObstacle()
            val rightmostEdge = obstacles.maxOfOrNull { it.x + it.w } ?: viewW
            val minGap = speed * 1.0f + 260f * scale
            val maxGap = speed * 1.9f + 420f * scale
            val nextGap = minGap + Random.nextFloat() * (maxGap - minGap)
            obstacleSpawnDistanceRemaining = max(1f, rightmostEdge - viewW + nextGap)
        }
        if (distanceSinceLastCoinRow >= nextCoinRowGap) { spawnCoinRow(); distanceSinceLastCoinRow = 0f; nextCoinRowGap = speed * 3.2f + Random.nextFloat() * speed * 2f }
        if (distanceSinceLastPowerUp >= nextPowerUpGap) { spawnPowerUp(); distanceSinceLastPowerUp = 0f; nextPowerUpGap = speed * 9f + Random.nextFloat() * speed * 6f }
    }
    private fun spawnObstacle() {
        val tier = currentDifficultyTier()
        val groundTypes = arrayOf(ObstacleType.SMALL_ROCK, ObstacleType.LARGE_ROCK, ObstacleType.CACTUS, ObstacleType.LOG, ObstacleType.BARRIER)
        val airTypes = arrayOf(ObstacleType.FLYER, ObstacleType.DRONE)
        val type = if (tier >= 1 && Random.nextFloat() < 0.30f + tier * 0.05f) airTypes[Random.nextInt(airTypes.size)] else groundTypes[Random.nextInt(groundTypes.size)]
        val h: Float; val w: Float; val y: Float
        when (type) {
            ObstacleType.SMALL_ROCK -> { w = 34f*scale; h = 34f*scale; y = groundY-h }
            ObstacleType.LARGE_ROCK -> { w = 52f*scale; h = 58f*scale; y = groundY-h }
            ObstacleType.CACTUS -> { w = 30f*scale; h = 70f*scale; y = groundY-h }
            ObstacleType.LOG -> { w = 90f*scale; h = 30f*scale; y = groundY-h }
            ObstacleType.BARRIER -> { w = 26f*scale; h = 80f*scale; y = groundY-h }
            ObstacleType.FLYER -> { w = 54f*scale; h = 30f*scale; y = groundY-dinoStandH*0.95f }
            ObstacleType.DRONE -> { w = 44f*scale; h = 28f*scale; y = groundY-dinoStandH*1.05f }
        }
        obstacles.add(Obstacle(type, viewW+w, y, w, h, baseY=y))
        if (tier >= 2 && Random.nextFloat() < 0.22f) {
            val comboGap = speed*1.3f + 300f*scale
            val comboType = if (type.isAir) groundTypes[Random.nextInt(groundTypes.size)] else airTypes[Random.nextInt(airTypes.size)]
            val cw: Float; val ch: Float; val cy: Float
            when (comboType) {
                ObstacleType.SMALL_ROCK -> { cw=34f*scale; ch=34f*scale; cy=groundY-ch }
                ObstacleType.LARGE_ROCK -> { cw=52f*scale; ch=58f*scale; cy=groundY-ch }
                ObstacleType.CACTUS -> { cw=30f*scale; ch=70f*scale; cy=groundY-ch }
                ObstacleType.LOG -> { cw=90f*scale; ch=30f*scale; cy=groundY-ch }
                ObstacleType.BARRIER -> { cw=26f*scale; ch=80f*scale; cy=groundY-ch }
                ObstacleType.FLYER -> { cw=54f*scale; ch=30f*scale; cy=groundY-dinoStandH*0.95f }
                ObstacleType.DRONE -> { cw=44f*scale; ch=28f*scale; cy=groundY-dinoStandH*1.05f }
            }
            obstacles.add(Obstacle(comboType, viewW+w+comboGap, cy, cw, ch, baseY=cy))
        }
    }
    private fun spawnCoinRow() {
        val count = 3 + Random.nextInt(3); val arched = Random.nextBoolean(); val spacing = 46f*scale; val startX = viewW+40f*scale
        for (i in 0 until count) {
            val x=startX+i*spacing
            val y=if(arched){val t=i/(count-1f).coerceAtLeast(1f); val arc=sin(t*Math.PI).toFloat(); groundY-dinoStandH*0.5f-arc*dinoStandH*0.9f}else groundY-26f*scale
            coins.add(Coin(x,y))
        }
    }
    private fun spawnPowerUp() { val types=PowerUpType.values(); val type=types[Random.nextInt(types.size)]; val y=groundY-dinoStandH*0.75f; powerUps.add(PowerUpEntity(viewW+40f*scale,y,type)) }
    private fun updateObstacles(dt: Float) {
        val it=obstacles.iterator(); val advance=speed*dt
        while(it.hasNext()){
            val o=it.next(); o.x-=advance
            if(o.type==ObstacleType.DRONE)o.wobble+=dt*3f
            if(o.x+o.w<0f){it.remove();continue}
            if(!o.passed&&o.x+o.w<dinoX){o.passed=true;obstaclesPassedThisRun++;combo++;comboTimer=comboGracePeriod;bestComboThisRun=max(bestComboThisRun,combo);if(combo>1)spawnBurst(dinoX+dinoW/2f,dinoY,Color.parseColor("#FFE27A"),min(12,4+combo))}
            val drawY=if(o.type==ObstacleType.DRONE)o.baseY+sin(o.wobble)*14f*scale else o.y
            otherHitbox.set(o.x,drawY,o.x+o.w,drawY+o.h);dinoHitboxNow()
            if(rectOverlapInset(dinoHitbox,otherHitbox))handleHit()
        }
    }
    private fun updateCoins(dt: Float) {
        val it = coins.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.x -= speed * dt
            if (c.x < -40f) {
                it.remove()
                continue
            }
            if (!c.collected) {
                if (activePowerUpLabel == "MAGNET" && activePowerUpTimeLeft > 0f) {
                    val dx=dinoX+dinoW/2f-c.x; val dy=dinoY+dinoH/2f-c.y; val d2=dx*dx+dy*dy; val radius=190f*scale
                    if(d2<radius*radius && d2>1f){val d=kotlin.math.sqrt(d2);val pull=(520f*scale*dt*(1f-d/radius)).coerceAtLeast(18f*scale*dt);c.x+=dx/d*pull;c.y+=dy/d*pull}
                }
                dinoHitboxNow()
                otherHitbox.set(
                    c.x - 14f * scale,
                    c.y - 14f * scale,
                    c.x + 14f * scale,
                    c.y + 14f * scale
                )
                if (rectOverlapInset(dinoHitbox, otherHitbox)) {
                    c.collected = true
                    coinsThisRun++
                    prefs.addCoins(1)
                    soundManager?.playCoin()
                    spawnBurst(c.x, c.y, Color.parseColor("#F4C430"), 6)
                    it.remove()
                }
            }
        }
    }
    private fun updatePowerUps(dt: Float) {
        val it = powerUps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x -= speed * dt
            if (p.x < -50f) {
                it.remove()
                continue
            }
            if (!p.collected) {
                dinoHitboxNow()
                val r = 20f * scale
                otherHitbox.set(p.x - r, p.y - r, p.x + r, p.y + r)
                if (rectOverlapInset(dinoHitbox, otherHitbox)) {
                    p.collected = true
                    applyPowerUp(p.type)
                    soundManager?.playPowerUp()
                    spawnBurst(p.x, p.y, Color.parseColor("#8FE3FF"), 12)
                    it.remove()
                }
            }
        }
    }
    private fun applyPowerUp(type: PowerUpType) {
        when(type){
            PowerUpType.SHIELD->{shieldActive=true;shieldTimer=6f;setActiveLabel("SHIELD",6f)}
            PowerUpType.MAGNET->{setActiveLabel("MAGNET",8f)}
            PowerUpType.SLOW_MO->{slowMoTimer=6f;setActiveLabel("SLOW-MO",6f)}
            PowerUpType.DOUBLE_SCORE->{doubleScoreTimer=8f;setActiveLabel("2x SCORE",8f)}
            PowerUpType.INVINCIBLE->{invincibleTimer=5f;setActiveLabel("INVINCIBLE",5f)}
            PowerUpType.DOUBLE_JUMP->{doubleJumpCharges=min(3,doubleJumpCharges+1);setActiveLabel("DOUBLE JUMP READY",0f)}
        }
    }
    private fun setActiveLabel(label:String,duration:Float){activePowerUpLabel=label;activePowerUpMaxDuration=if(duration>0f)duration else 1f;activePowerUpTimeLeft=duration}
    private fun powerUpIsActive(type: PowerUpType): Boolean = when(type) {
        PowerUpType.SHIELD -> shieldActive
        PowerUpType.MAGNET -> activePowerUpLabel == "MAGNET" && activePowerUpTimeLeft > 0f
        PowerUpType.SLOW_MO -> slowMoTimer > 0f
        PowerUpType.DOUBLE_SCORE -> doubleScoreTimer > 0f
        PowerUpType.INVINCIBLE -> invincibleTimer > 0f
        PowerUpType.DOUBLE_JUMP -> doubleJumpCharges > 0
    }
    private fun updateEffects(dt:Float){
        if(shieldTimer>0f){shieldTimer-=dt;if(shieldTimer<=0f){shieldTimer=0f;shieldActive=false;clearLabelIfMatches("SHIELD")}}
        if(slowMoTimer>0f){slowMoTimer-=dt;if(slowMoTimer<=0f)clearLabelIfMatches("SLOW-MO")}
        if(doubleScoreTimer>0f){doubleScoreTimer-=dt;if(doubleScoreTimer<=0f)clearLabelIfMatches("2x SCORE")}
        if(invincibleTimer>0f){invincibleTimer-=dt;if(invincibleTimer<=0f)clearLabelIfMatches("INVINCIBLE")}
        if(activePowerUpTimeLeft>0f)activePowerUpTimeLeft=max(0f,activePowerUpTimeLeft-dt)
        if(screenShakeTimer>0f)screenShakeTimer=max(0f,screenShakeTimer-dt)
        if(activePowerUpLabel=="MAGNET" && activePowerUpTimeLeft<=0f)clearLabelIfMatches("MAGNET")
    }
    private fun clearLabelIfMatches(label: String) {
        if (activePowerUpLabel == label) {
            activePowerUpLabel = when {
                shieldActive -> "SHIELD"
                invincibleTimer > 0f -> "INVINCIBLE"
                doubleScoreTimer > 0f -> "2x SCORE"
                slowMoTimer > 0f -> "SLOW-MO"
                activePowerUpLabel == "MAGNET" && activePowerUpTimeLeft > 0f -> "MAGNET"
                else -> null
            }
        }
    }
    private fun updateParticles(dt: Float) {
        val it=particles.iterator()
        while(it.hasNext()){val p=it.next();p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=500f*dt;p.life-=dt;if(p.life<=0f)it.remove()}
    }
    private fun spawnBurst(x:Float,y:Float,color:Int,count:Int){
        val quality = prefs.graphicsQuality.coerceIn(0, 2)
        if (quality == 0) return
        val actualCount = if (quality == 2) ceil(count * 1.5f).toInt() else count
        repeat(actualCount){val angle=Random.nextFloat()*(Math.PI*2).toFloat();val speedP=80f+Random.nextFloat()*160f;particles.add(Particle(x,y,kotlin.math.cos(angle)*speedP,kotlin.math.sin(angle)*speedP,0.5f,0.5f,color,4f*scale))}
    }
    private fun dinoHitboxNow(){val insetX=dinoW*0.18f;val insetY=dinoH*0.14f;dinoHitbox.set(dinoX+insetX,dinoY+insetY,dinoX+dinoW-insetX,dinoY+dinoH-insetY)}
    private fun rectOverlap(a:RectF,b:RectF):Boolean=a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top
    private fun rectOverlapInset(a:RectF,b:RectF):Boolean{val i=RectF(b.left+b.width()*0.12f,b.top+b.height()*0.12f,b.right-b.width()*0.12f,b.bottom-b.height()*0.12f);return rectOverlap(a,i)}
    private fun handleHit(){
        if(invincibleTimer>0f)return
        if(shieldActive){shieldActive=false;shieldTimer=0f;if(activePowerUpLabel=="SHIELD")activePowerUpLabel=null;spawnBurst(dinoX+dinoW/2f,dinoY+dinoH/2f,Color.parseColor("#6FD8FF"),16);screenShakeTimer=0.18f;screenShakeStrength=7f*scale;soundManager?.vibrate(40);return}
        combo=0;screenShakeTimer=0.28f;screenShakeStrength=10f*scale
        gameOver()
    }
    private fun gameOver(){
        if(state==State.GAME_OVER)return
        state=State.GAME_OVER;stopRenderLoop();soundManager?.playCrash();soundManager?.vibrate(150);spawnBurst(dinoX+dinoW/2f,dinoY+dinoH/2f,Color.parseColor("#C0392B"),20)
        val distMeters=(distanceTraveled/pixelsPerMeter).toInt()
        prefs.addJumps(jumpsThisRun);prefs.addObstaclesPassed(obstaclesPassedThisRun)
        val isNewBest=prefs.reportRunFinished(score,distMeters)
        activePowerUpLabel=null; activePowerUpTimeLeft=0f
        if(isNewBest)soundManager?.playHighScore()else soundManager?.playGameOver()
        listener?.onGameOver(score,prefs.bestScore,distMeters,coinsThisRun,isNewBest)
    }
    companion object{private const val ENV_COUNT=5}

    private val envPalettes=arrayOf(
        intArrayOf(0xFF8FD3F4.toInt(),0xFFE8F8FF.toInt(),0xFFFF9E6D.toInt(),0xFFFFD3A0.toInt(),0xFF0B1730.toInt(),0xFF1C2C4A.toInt()),
        intArrayOf(0xFF7FC9A0.toInt(),0xFFDFF3E4.toInt(),0xFFE68A6C.toInt(),0xFFF7C99B.toInt(),0xFF0C1F1B.toInt(),0xFF16332B.toInt()),
        intArrayOf(0xFF9FC9E8.toInt(),0xFFEFF6FB.toInt(),0xFFEB8FA0.toInt(),0xFFF7D0C4.toInt(),0xFF10192E.toInt(),0xFF223050.toInt()),
        intArrayOf(0xFF7C8FB8.toInt(),0xFFD9E1EE.toInt(),0xFFB07AC4.toInt(),0xFFF0B0C6.toInt(),0xFF07081A.toInt(),0xFF1A1830.toInt()),
        intArrayOf(0xFFE7C989.toInt(),0xFFF7E9C7.toInt(),0xFFD98850.toInt(),0xFFF0B37A.toInt(),0xFF201308.toInt(),0xFF3A2413.toInt())
    )
    private fun lerpColor(a:Int,b:Int,t:Float):Int{
        val tt=t.coerceIn(0f,1f);val ar=Color.red(a);val ag=Color.green(a);val ab=Color.blue(a);val br=Color.red(b);val bg=Color.green(b);val bb=Color.blue(b)
        return Color.rgb((ar+(br-ar)*tt).toInt(),(ag+(bg-ag)*tt).toInt(),(ab+(bb-ab)*tt).toInt())
    }
    private fun skyColorsForPhase():Pair<Int,Int>{
        val p=envPalettes[environmentIndex];val day=Pair(p[0],p[1]);val sunset=Pair(p[2],p[3]);val night=Pair(p[4],p[5])
        return when{dayNightPhase<0.25f->{val t=dayNightPhase/0.25f;Pair(lerpColor(day.first,sunset.first,t),lerpColor(day.second,sunset.second,t))}
            dayNightPhase<0.5f->{val t=(dayNightPhase-0.25f)/0.25f;Pair(lerpColor(sunset.first,night.first,t),lerpColor(sunset.second,night.second,t))}
            dayNightPhase<0.75f->{val t=(dayNightPhase-0.5f)/0.25f;Pair(lerpColor(night.first,sunset.first,t),lerpColor(night.second,sunset.second,t))}
            else->{val t=(dayNightPhase-0.75f)/0.25f;Pair(lerpColor(sunset.first,day.first,t),lerpColor(sunset.second,day.second,t))}}
    }
    override fun onDraw(canvas:Canvas){
        if(viewW<=0f||viewH<=0f)return
        val save=canvas.save()
        if(screenShakeTimer>0f && prefs.graphicsQuality>=1){
            val fade=(screenShakeTimer/0.28f).coerceIn(0f,1f)
            canvas.translate((Random.nextFloat()*2f-1f)*screenShakeStrength*fade,(Random.nextFloat()*2f-1f)*screenShakeStrength*fade)
        }
        val(skyTop,skyBottom)=skyColorsForPhase()
        skyPaint.shader=LinearGradient(0f,0f,0f,groundY,skyTop,skyBottom,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,viewW,groundY,skyPaint);drawParallaxHills(canvas,skyBottom);drawGround(canvas);dinoHitboxNow()
        for(o in obstacles)drawObstacle(canvas,o);for(c in coins)if(!c.collected)drawCoin(canvas,c);for(p in powerUps)if(!p.collected)drawPowerUp(canvas,p);drawDino(canvas);drawParticles(canvas)
        if(prefs.graphicsQuality>=2 && speed>baseSpeed*1.45f) drawSpeedLines(canvas)
        canvas.restoreToCount(save)
    }
    private fun drawSpeedLines(canvas:Canvas){
        val p=particlePaint
        p.style=Paint.Style.STROKE; p.strokeWidth=2f*scale; p.color=Color.WHITE; p.alpha=45
        repeat(5){ val y=viewH*0.18f+Random.nextFloat()*viewH*0.45f; val x=Random.nextFloat()*viewW; canvas.drawLine(x,y,x-32f*scale,y,p) }
        p.style=Paint.Style.FILL
    }
    private fun drawParallaxHills(canvas:Canvas,tint:Int){
        val quality = prefs.graphicsQuality.coerceIn(0, 2)
        hillPaint.color=tint
        hillPaint.alpha=if(quality==0) 90 else if(quality==2) 160 else 140
        val step=if(quality==0) 420f*scale else if(quality==2) 210f*scale else 260f*scale
        val width=if(quality==2) 190f*scale else 220f*scale
        val offset=(distanceTraveled*if(quality==2) 0.16f else 0.12f)%(viewW+step)
        var x=-offset
        while(x<viewW){canvas.drawOval(x,groundY-90f*scale,x+width,groundY+40f*scale,hillPaint);x+=step}
        if(dayNightPhase>0.50f && dayNightPhase<0.86f){
            val starPaint=particlePaint
            starPaint.style=Paint.Style.FILL; starPaint.color=Color.WHITE; starPaint.alpha=if(quality==2)170 else 100
            repeat(if(quality==2)16 else 7){ val sx=(it*113f+distanceTraveled*0.008f)%(viewW+16f); val sy=28f*scale+(it*47f)%(groundY*0.42f); canvas.drawCircle(sx,sy,1.1f*scale,starPaint) }
            starPaint.color=Color.parseColor("#FFF1B8"); starPaint.alpha=210
            canvas.drawCircle(viewW*0.82f,viewH*0.18f,22f*scale,starPaint)
        }
    }
    private fun drawGround(canvas:Canvas){
        canvas.drawRect(0f,groundY,viewW,viewH,groundPaint);val segment=46f*scale;val offset=distanceTraveled%segment;var x=-offset
        while(x<viewW){canvas.drawLine(x,groundY+6f*scale,x+segment*0.5f,groundY+6f*scale,groundLinePaint);x+=segment}
    }
    private fun drawObstacle(canvas:Canvas,o:Obstacle){
        val drawY=if(o.type==ObstacleType.DRONE)o.baseY+sin(o.wobble)*14f*scale else o.y
        obstaclePaint.color=when(o.type){ObstacleType.SMALL_ROCK,ObstacleType.LARGE_ROCK->Color.parseColor("#7C7368");ObstacleType.CACTUS->Color.parseColor("#3E7D4C");ObstacleType.LOG->Color.parseColor("#6B4A2F");ObstacleType.BARRIER->Color.parseColor("#B04A3A");ObstacleType.FLYER->Color.parseColor("#8E4FBF");ObstacleType.DRONE->Color.parseColor("#3B3F45")}
        when(o.type){
            ObstacleType.SMALL_ROCK,ObstacleType.LARGE_ROCK->canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,8f*scale,8f*scale,obstaclePaint)
            ObstacleType.CACTUS->{canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,6f*scale,6f*scale,obstaclePaint);canvas.drawRoundRect(o.x-o.w*0.5f,drawY+o.h*0.25f,o.x,drawY+o.h*0.55f,5f*scale,5f*scale,obstaclePaint)}
            ObstacleType.LOG->canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,14f*scale,14f*scale,obstaclePaint)
            ObstacleType.BARRIER->canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,4f*scale,4f*scale,obstaclePaint)
            ObstacleType.FLYER->{val cx=o.x+o.w/2f;val cy=drawY+o.h/2f;canvas.drawOval(o.x,drawY,o.x+o.w,drawY+o.h,obstaclePaint);val wingFlap=sin(runPhase*1.6f)*o.h*0.5f;canvas.drawOval(cx-o.w*0.3f,cy-o.h*0.5f-wingFlap,cx,cy,obstaclePaint);canvas.drawOval(cx,cy-o.h*0.5f-wingFlap,cx+o.w*0.3f,cy,obstaclePaint)}
            ObstacleType.DRONE->{canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,8f*scale,8f*scale,obstaclePaint);obstaclePaint.color=if((runPhase.toInt()%2)==0)Color.RED else Color.parseColor("#552222");canvas.drawCircle(o.x+o.w/2f,drawY+o.h/2f,4f*scale,obstaclePaint)}
        }
        canvas.drawRoundRect(o.x,drawY,o.x+o.w,drawY+o.h,6f*scale,6f*scale,obstacleOutline)
    }
    private fun drawCoin(canvas:Canvas,c:Coin){val r=14f*scale;canvas.drawCircle(c.x,c.y,r,coinPaint);canvas.drawCircle(c.x-r*0.3f,c.y-r*0.3f,r*0.35f,coinShinePaint)}
    private fun drawPowerUp(canvas:Canvas,p:PowerUpEntity){
        val r=20f*scale;obstaclePaint.color=when(p.type){PowerUpType.SHIELD->Color.parseColor("#3E7BD9");PowerUpType.MAGNET->Color.parseColor("#E06BCB");PowerUpType.SLOW_MO->Color.parseColor("#7C58C9");PowerUpType.DOUBLE_SCORE->Color.parseColor("#D9A23E");PowerUpType.INVINCIBLE->Color.parseColor("#D93E6B");PowerUpType.DOUBLE_JUMP->Color.parseColor("#3ED98C")}
        canvas.drawCircle(p.x,p.y,r,obstaclePaint);val glyph=when(p.type){PowerUpType.SHIELD->"S";PowerUpType.MAGNET->"M";PowerUpType.SLOW_MO->"Z";PowerUpType.DOUBLE_SCORE->"2x";PowerUpType.INVINCIBLE->"★";PowerUpType.DOUBLE_JUMP->"↑↑"};powerUpTextPaint.textSize=16f*scale;canvas.drawText(glyph,p.x,p.y+6f*scale,powerUpTextPaint)
    }
    private fun drawDino(canvas:Canvas){
        dinoBodyPaint.color=if(state==State.GAME_OVER)Color.parseColor("#B04A3A")else if(invincibleTimer>0f&&(runPhase.toInt()%2==0))Color.parseColor("#8FE3FF")else Color.parseColor("#3FA687")
        val bx=dinoX;val by=dinoY;val bw=dinoW;val bh=dinoH
        canvas.drawRoundRect(bx,by,bx+bw,by+bh,14f*scale,14f*scale,dinoBodyPaint)
        canvas.drawRoundRect(bx+bw*0.15f,by+bh*0.45f,bx+bw*0.85f,by+bh*0.95f,10f*scale,10f*scale,dinoBellyPaint)
        canvas.drawCircle(bx+bw*0.72f,by+bh*0.28f,4f*scale,dinoEyePaint)
        val legOffset=if(onGround)sin(runPhase)*bh*0.12f else 0f;val legW=bw*0.18f;val legH=bh*0.22f
        canvas.drawRoundRect(bx+bw*0.2f,by+bh-legH*0.5f+legOffset,bx+bw*0.2f+legW,by+bh+legH*0.5f+legOffset,4f*scale,4f*scale,dinoBodyPaint)
        canvas.drawRoundRect(bx+bw*0.55f,by+bh-legH*0.5f-legOffset,bx+bw*0.55f+legW,by+bh+legH*0.5f-legOffset,4f*scale,4f*scale,dinoBodyPaint)
        canvas.drawRoundRect(bx-bw*0.18f,by+bh*0.25f,bx,by+bh*0.55f,8f*scale,8f*scale,dinoBodyPaint)
        if(shieldActive)canvas.drawCircle(bx+bw/2f,by+bh/2f,max(bw,bh)*0.75f,shieldRingPaint)
    }
    private fun drawParticles(canvas:Canvas){
        if (prefs.graphicsQuality == 0) return
        for(p in particles){particlePaint.color=p.color;particlePaint.alpha=(255*(p.life/p.maxLife)).toInt().coerceIn(0,255);canvas.drawCircle(p.x,p.y,p.radius,particlePaint)}
    }
}
