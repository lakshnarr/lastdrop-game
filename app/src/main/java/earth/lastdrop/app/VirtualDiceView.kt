package earth.lastdrop.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * True 3D dice with proper cube geometry, rotation matrices, and perspective projection
 */
class VirtualDiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val logTag = "VirtualDiceView"

    private var isAnimating = false
    private var rotX = 0.0
    private var rotY = 0.0
    private var rotZ = 0.0

    // Rolling physics (radians/second)
    private var omegaX = 0.0
    private var omegaY = 0.0
    private var omegaZ = 0.0
    private var lastAnimTimeMs = 0L

    private var tumbleX = 0.0
    private var tumbleY = 0.0
    private var tumbleZ = 0.0

    private var dirX = 1
    private var dirY = 1
    private var dirZ = 1

    private var cubeSize = 50.0
    private var perspective = 400.0
    private val projectionFlatness = 1.0 // 1 = orthographic, 0 = perspective

    // Brand palette (matches WEB_ECOSYSTEM_PLAN.md)
    private val brandBase = Color.parseColor("#0B1020")
    private val brandShadow = Color.parseColor("#050711")
    private val brandHighlight = Color.parseColor("#16213D")
    private val accentPrimary = Color.parseColor("#40E0D0")
    private val accentSecondary = Color.parseColor("#4ADE80")
    private val pipBase = Color.parseColor("#E5E7EB")

    // Define 8 vertices of a cube centered at origin
    private val vertices = arrayOf(
        Point3D(-1.0, -1.0, -1.0), Point3D(1.0, -1.0, -1.0),
        Point3D(1.0, 1.0, -1.0), Point3D(-1.0, 1.0, -1.0),
        Point3D(-1.0, -1.0, 1.0), Point3D(1.0, -1.0, 1.0),
        Point3D(1.0, 1.0, 1.0), Point3D(-1.0, 1.0, 1.0)
    )
    
    // Define 6 faces (each face has 4 vertex indices)
    private val faces = arrayOf(
        Face(intArrayOf(0, 1, 2, 3), 1),  // Front - value 1
        Face(intArrayOf(5, 4, 7, 6), 6),  // Back - value 6
        Face(intArrayOf(4, 0, 3, 7), 2),  // Left - value 2
        Face(intArrayOf(1, 5, 6, 2), 5),  // Right - value 5
        Face(intArrayOf(3, 2, 6, 7), 3),  // Top - value 3
        Face(intArrayOf(4, 5, 1, 0), 4)   // Bottom - value 4
    )
    
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pipBase
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#40101010"))
    }
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4026C6DA")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(32f, BlurMaskFilter.Blur.NORMAL)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4040E0D0")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 12f), 0f)
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    init {
        // Start with a nice viewing angle
        rotX = 0.0
        rotY = 0.0
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toDouble()
        cubeSize = s * 0.22
        perspective = max(300.0, cubeSize * 6.0)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Draw glow + shadow to anchor cube
        drawGlowLayer(canvas, centerX, centerY)
        drawShadowLayer(canvas, centerX, centerY)
        
        // Transform all vertices
        val transformed = vertices.map { v ->
            var p = Point3D(v.x * cubeSize, v.y * cubeSize, v.z * cubeSize)
            p = rotateX(p, rotX)
            p = rotateY(p, rotY)
            p = rotateZ(p, rotZ)
            p
        }
        
        // Calculate face data for sorting
        val faceData = faces.map { face ->
            // Calculate face center for depth sorting
            val center = Point3D(
                transformed[face.vertices[0]].x + transformed[face.vertices[1]].x + 
                transformed[face.vertices[2]].x + transformed[face.vertices[3]].x,
                transformed[face.vertices[0]].y + transformed[face.vertices[1]].y + 
                transformed[face.vertices[2]].y + transformed[face.vertices[3]].y,
                transformed[face.vertices[0]].z + transformed[face.vertices[1]].z + 
                transformed[face.vertices[2]].z + transformed[face.vertices[3]].z
            )
            
            // Calculate face normal for backface culling
            val v1 = transformed[face.vertices[1]].subtract(transformed[face.vertices[0]])
            val v2 = transformed[face.vertices[2]].subtract(transformed[face.vertices[0]])
            val normal = v1.cross(v2)
            
            FaceData(face, center.z, normal.z)
        }
        
        // Sort faces back to front
        val sortedFaces = faceData.sortedBy { it.depth }
        
        // Draw each face
        for ((index, fd) in sortedFaces.withIndex()) {
            // Skip back-facing faces
            if (fd.normalZ >= 0) continue
            
            val face = fd.face
            val faceIndex = faces.indexOf(face)
            
            // Project vertices to 2D
            val points = face.vertices.map { idx ->
                project(transformed[idx], centerX, centerY)
            }

            val bounds = computeBounds(points)

            // Calculate shading based on face orientation
            val brightness = calculateBrightness(transformed, face.vertices)
            styleFacePaint(points, brightness, faceIndex)
            
            // Draw face
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
            path.close()
            
            canvas.drawPath(path, facePaint)
            canvas.drawPath(path, edgePaint)
            drawFaceTexture(canvas, path, bounds, brightness)
            drawSpecularHighlight(canvas, bounds, brightness)
            facePaint.shader = null
            
            // Draw pips - transform them in 3D space
            val pipPositions3D = getPipPositionsForFace(faceIndex, face.value)
            val pipRadius = (cubeSize * 0.18) // Slightly larger pips
            
            pipPaint.alpha = (235 * brightness).toInt().coerceIn(180, 255)
            
            for (pipCenter in pipPositions3D) {
                // Generate polygon vertices for the pip (8-sided polygon to approximate circle)
                val pipVertices = generatePipPolygon(pipCenter, faceIndex, pipRadius)
                
                // Transform and project pip vertices
                val projectedPip = pipVertices.map { v ->
                    var p = v
                    p = rotateX(p, rotX)
                    p = rotateY(p, rotY)
                    p = rotateZ(p, rotZ)
                    project(p, centerX, centerY)
                }
                
                // Draw pip polygon
                val pipPath = Path()
                pipPath.moveTo(projectedPip[0].x, projectedPip[0].y)
                projectedPip.drop(1).forEach { p -> pipPath.lineTo(p.x, p.y) }
                pipPath.close()
                
                pipPaint.color = interpolateColor(pipBase, accentPrimary, 0.25f + brightness * 0.5f)
                canvas.drawPath(pipPath, pipPaint)
            }
            pipPaint.alpha = 255
        }
    }

    private fun drawGlowLayer(canvas: Canvas, centerX: Float, centerY: Float) {
        val radius = (cubeSize * 2.2).toFloat()
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            Color.parseColor("#4030E0D0"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawShadowLayer(canvas: Canvas, centerX: Float, centerY: Float) {
        val shadowWidth = cubeSize.toFloat() * 1.6f
        val shadowHeight = cubeSize.toFloat() * 0.6f
        val rect = RectF(
            centerX - shadowWidth / 2f,
            centerY + cubeSize.toFloat() * 0.9f,
            centerX + shadowWidth / 2f,
            centerY + cubeSize.toFloat() * 0.9f + shadowHeight
        )
        canvas.drawOval(rect, shadowPaint)
    }

    private fun computeBounds(points: List<Point2D>): RectF {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        return RectF(minX, minY, maxX, maxY)
    }

    private fun styleFacePaint(points: List<Point2D>, brightness: Float, faceIndex: Int) {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val accent = if (faceIndex % 2 == 0) accentPrimary else accentSecondary
        val highlight = interpolateColor(accent, brandHighlight, 0.4f + brightness * 0.4f)
        val shadow = interpolateColor(brandBase, brandShadow, 0.6f)
        facePaint.shader = LinearGradient(minX, minY, maxX, maxY, highlight, shadow, Shader.TileMode.CLAMP)
    }

    private fun drawFaceTexture(canvas: Canvas, path: Path, bounds: RectF, brightness: Float) {
        canvas.save()
        canvas.clipPath(path)
        val spacing = 12f
        val alpha = (25 + brightness * 50).toInt().coerceIn(15, 80)
        texturePaint.alpha = alpha
        var y = bounds.top - bounds.width()
        while (y < bounds.bottom + bounds.width()) {
            canvas.drawLine(
                bounds.left - bounds.height(),
                y,
                bounds.right + bounds.height(),
                y + bounds.width() * 0.4f,
                texturePaint
            )
            y += spacing
        }
        canvas.restore()
    }

    private fun drawSpecularHighlight(canvas: Canvas, bounds: RectF, brightness: Float) {
        val radius = bounds.width().coerceAtLeast(bounds.height()) * 0.35f
        val cx = bounds.left + bounds.width() * 0.35f
        val cy = bounds.top + bounds.height() * 0.35f
        val highlight = Color.argb((80 * brightness).toInt().coerceIn(25, 120), 255, 255, 255)
        val transparent = Color.argb(0, 255, 255, 255)
        specularPaint.shader = RadialGradient(cx, cy, radius, highlight, transparent, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, specularPaint)
        specularPaint.shader = null
    }
    
    private fun generatePipPolygon(center: Point3D, faceIndex: Int, radius: Double): List<Point3D> {
        val points = mutableListOf<Point3D>()
        val segments = 12
        val angleStep = 2 * Math.PI / segments
        
        for (i in 0 until segments) {
            val angle = i * angleStep
            val cos = cos(angle) * radius
            val sin = sin(angle) * radius
            
            // Add offset to center based on face orientation
            val p = when (faceIndex) {
                0, 1 -> Point3D(center.x + cos, center.y + sin, center.z) // Front/Back (Z plane)
                2, 3 -> Point3D(center.x, center.y + cos, center.z + sin) // Left/Right (X plane)
                4, 5 -> Point3D(center.x + cos, center.y, center.z + sin) // Top/Bottom (Y plane)
                else -> center
            }
            points.add(p)
        }
        return points
    }
    
    private fun project(p: Point3D, centerX: Float, centerY: Float): Point2D {
        val perspectiveScale = perspective / (perspective + p.z)
        val scale = lerp(perspectiveScale, 1.0, projectionFlatness)
        return Point2D(
            centerX + (p.x * scale).toFloat(),
            centerY - (p.y * scale).toFloat()
        )
    }
    
    private fun rotateX(p: Point3D, angle: Double): Point3D {
        val cos = cos(angle)
        val sin = sin(angle)
        return Point3D(
            p.x,
            p.y * cos - p.z * sin,
            p.y * sin + p.z * cos
        )
    }
    
    private fun rotateY(p: Point3D, angle: Double): Point3D {
        val cos = cos(angle)
        val sin = sin(angle)
        return Point3D(
            p.x * cos + p.z * sin,
            p.y,
            -p.x * sin + p.z * cos
        )
    }
    
    private fun rotateZ(p: Point3D, angle: Double): Point3D {
        val cos = cos(angle)
        val sin = sin(angle)
        return Point3D(
            p.x * cos - p.y * sin,
            p.x * sin + p.y * cos,
            p.z
        )
    }
    
    private fun calculateBrightness(vertices: List<Point3D>, indices: IntArray): Float {
        // Simple lighting based on face orientation
        val v1 = vertices[indices[1]].subtract(vertices[indices[0]])
        val v2 = vertices[indices[2]].subtract(vertices[indices[0]])
        val normal = v1.cross(v2).normalize()
        
        // Light direction (from top-front-right)
        val light = Point3D(0.5, -0.7, -0.5).normalize()
        val dot = normal.dot(light)
        
        return (0.4f + 0.6f * dot.toFloat()).coerceIn(0.3f, 1.0f)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        return a + (b - a) * clamped
    }

    private fun smoothstep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun wrapAngleRad(a: Double): Double {
        val twoPi = 2.0 * Math.PI
        var x = a % twoPi
        if (x <= -Math.PI) x += twoPi
        if (x > Math.PI) x -= twoPi
        return x
    }

    private fun lerpAngleRad(current: Double, target: Double, t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        val delta = wrapAngleRad(target - current)
        return current + delta * clamped
    }

    private fun lerpAngleRadDirectional(current: Double, target: Double, t: Double, direction: Int): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        val twoPi = 2.0 * Math.PI
        var delta = wrapAngleRad(target - current)

        val dir = if (direction >= 0) 1 else -1
        if (dir > 0 && delta < 0.0) delta += twoPi
        if (dir < 0 && delta > 0.0) delta -= twoPi

        return current + delta * clamped
    }

    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * ratio + Color.red(color2) * (1 - ratio)).toInt()
        val g = (Color.green(color1) * ratio + Color.green(color2) * (1 - ratio)).toInt()
        val b = (Color.blue(color1) * ratio + Color.blue(color2) * (1 - ratio)).toInt()
        return Color.rgb(r, g, b)
    }
    
    private fun getPipPositionsForFace(faceIndex: Int, value: Int): List<Point3D> {
        val pips = mutableListOf<Point3D>()
        val offset = 0.35 // Pip position offset from center
        
        // Define pip positions in 3D space for each face
        // Each face is in its local coordinate system
        when (faceIndex) {
            // NOTE: Our cube vertex "front" is Z = -1 (closer to camera in projection)
            0 -> { // Front face (Z = -1)
                when (value) {
                    1 -> pips.add(Point3D(0.0, 0.0, -1.0))
                    2 -> {
                        pips.add(Point3D(-offset, -offset, -1.0))
                        pips.add(Point3D(offset, offset, -1.0))
                    }
                    3 -> {
                        pips.add(Point3D(-offset, -offset, -1.0))
                        pips.add(Point3D(0.0, 0.0, -1.0))
                        pips.add(Point3D(offset, offset, -1.0))
                    }
                    4 -> {
                        pips.add(Point3D(-offset, -offset, -1.0))
                        pips.add(Point3D(offset, -offset, -1.0))
                        pips.add(Point3D(-offset, offset, -1.0))
                        pips.add(Point3D(offset, offset, -1.0))
                    }
                    5 -> {
                        pips.add(Point3D(-offset, -offset, -1.0))
                        pips.add(Point3D(offset, -offset, -1.0))
                        pips.add(Point3D(0.0, 0.0, -1.0))
                        pips.add(Point3D(-offset, offset, -1.0))
                        pips.add(Point3D(offset, offset, -1.0))
                    }
                    6 -> {
                        pips.add(Point3D(-offset, -offset, -1.0))
                        pips.add(Point3D(-offset, 0.0, -1.0))
                        pips.add(Point3D(-offset, offset, -1.0))
                        pips.add(Point3D(offset, -offset, -1.0))
                        pips.add(Point3D(offset, 0.0, -1.0))
                        pips.add(Point3D(offset, offset, -1.0))
                    }
                }
            }
            1 -> { // Back face (Z = 1)
                when (value) {
                    1 -> pips.add(Point3D(0.0, 0.0, 1.0))
                    2 -> {
                        pips.add(Point3D(-offset, -offset, 1.0))
                        pips.add(Point3D(offset, offset, 1.0))
                    }
                    3 -> {
                        pips.add(Point3D(-offset, -offset, 1.0))
                        pips.add(Point3D(0.0, 0.0, 1.0))
                        pips.add(Point3D(offset, offset, 1.0))
                    }
                    4 -> {
                        pips.add(Point3D(-offset, -offset, 1.0))
                        pips.add(Point3D(offset, -offset, 1.0))
                        pips.add(Point3D(-offset, offset, 1.0))
                        pips.add(Point3D(offset, offset, 1.0))
                    }
                    5 -> {
                        pips.add(Point3D(-offset, -offset, 1.0))
                        pips.add(Point3D(offset, -offset, 1.0))
                        pips.add(Point3D(0.0, 0.0, 1.0))
                        pips.add(Point3D(-offset, offset, 1.0))
                        pips.add(Point3D(offset, offset, 1.0))
                    }
                    6 -> {
                        pips.add(Point3D(-offset, -offset, 1.0))
                        pips.add(Point3D(-offset, 0.0, 1.0))
                        pips.add(Point3D(-offset, offset, 1.0))
                        pips.add(Point3D(offset, -offset, 1.0))
                        pips.add(Point3D(offset, 0.0, 1.0))
                        pips.add(Point3D(offset, offset, 1.0))
                    }
                }
            }
            2 -> { // Left face (X = -1)
                when (value) {
                    1 -> pips.add(Point3D(-1.0, 0.0, 0.0))
                    2 -> {
                        pips.add(Point3D(-1.0, -offset, -offset))
                        pips.add(Point3D(-1.0, offset, offset))
                    }
                    3 -> {
                        pips.add(Point3D(-1.0, -offset, -offset))
                        pips.add(Point3D(-1.0, 0.0, 0.0))
                        pips.add(Point3D(-1.0, offset, offset))
                    }
                    4 -> {
                        pips.add(Point3D(-1.0, -offset, -offset))
                        pips.add(Point3D(-1.0, offset, -offset))
                        pips.add(Point3D(-1.0, -offset, offset))
                        pips.add(Point3D(-1.0, offset, offset))
                    }
                    5 -> {
                        pips.add(Point3D(-1.0, -offset, -offset))
                        pips.add(Point3D(-1.0, offset, -offset))
                        pips.add(Point3D(-1.0, 0.0, 0.0))
                        pips.add(Point3D(-1.0, -offset, offset))
                        pips.add(Point3D(-1.0, offset, offset))
                    }
                    6 -> {
                        pips.add(Point3D(-1.0, -offset, -offset))
                        pips.add(Point3D(-1.0, 0.0, -offset))
                        pips.add(Point3D(-1.0, offset, -offset))
                        pips.add(Point3D(-1.0, -offset, offset))
                        pips.add(Point3D(-1.0, 0.0, offset))
                        pips.add(Point3D(-1.0, offset, offset))
                    }
                }
            }
            3 -> { // Right face (X = 1)
                when (value) {
                    1 -> pips.add(Point3D(1.0, 0.0, 0.0))
                    2 -> {
                        pips.add(Point3D(1.0, -offset, -offset))
                        pips.add(Point3D(1.0, offset, offset))
                    }
                    3 -> {
                        pips.add(Point3D(1.0, -offset, -offset))
                        pips.add(Point3D(1.0, 0.0, 0.0))
                        pips.add(Point3D(1.0, offset, offset))
                    }
                    4 -> {
                        pips.add(Point3D(1.0, -offset, -offset))
                        pips.add(Point3D(1.0, offset, -offset))
                        pips.add(Point3D(1.0, -offset, offset))
                        pips.add(Point3D(1.0, offset, offset))
                    }
                    5 -> {
                        pips.add(Point3D(1.0, -offset, -offset))
                        pips.add(Point3D(1.0, offset, -offset))
                        pips.add(Point3D(1.0, 0.0, 0.0))
                        pips.add(Point3D(1.0, -offset, offset))
                        pips.add(Point3D(1.0, offset, offset))
                    }
                    6 -> {
                        pips.add(Point3D(1.0, -offset, -offset))
                        pips.add(Point3D(1.0, 0.0, -offset))
                        pips.add(Point3D(1.0, offset, -offset))
                        pips.add(Point3D(1.0, -offset, offset))
                        pips.add(Point3D(1.0, 0.0, offset))
                        pips.add(Point3D(1.0, offset, offset))
                    }
                }
            }
            4 -> { // Top face (Y = 1)
                when (value) {
                    1 -> pips.add(Point3D(0.0, 1.0, 0.0))
                    2 -> {
                        pips.add(Point3D(-offset, 1.0, -offset))
                        pips.add(Point3D(offset, 1.0, offset))
                    }
                    3 -> {
                        pips.add(Point3D(-offset, 1.0, -offset))
                        pips.add(Point3D(0.0, 1.0, 0.0))
                        pips.add(Point3D(offset, 1.0, offset))
                    }
                    4 -> {
                        pips.add(Point3D(-offset, 1.0, -offset))
                        pips.add(Point3D(offset, 1.0, -offset))
                        pips.add(Point3D(-offset, 1.0, offset))
                        pips.add(Point3D(offset, 1.0, offset))
                    }
                    5 -> {
                        pips.add(Point3D(-offset, 1.0, -offset))
                        pips.add(Point3D(offset, 1.0, -offset))
                        pips.add(Point3D(0.0, 1.0, 0.0))
                        pips.add(Point3D(-offset, 1.0, offset))
                        pips.add(Point3D(offset, 1.0, offset))
                    }
                    6 -> {
                        pips.add(Point3D(-offset, 1.0, -offset))
                        pips.add(Point3D(0.0, 1.0, -offset))
                        pips.add(Point3D(offset, 1.0, -offset))
                        pips.add(Point3D(-offset, 1.0, offset))
                        pips.add(Point3D(0.0, 1.0, offset))
                        pips.add(Point3D(offset, 1.0, offset))
                    }
                }
            }
            5 -> { // Bottom face (Y = -1)
                when (value) {
                    1 -> pips.add(Point3D(0.0, -1.0, 0.0))
                    2 -> {
                        pips.add(Point3D(-offset, -1.0, -offset))
                        pips.add(Point3D(offset, -1.0, offset))
                    }
                    3 -> {
                        pips.add(Point3D(-offset, -1.0, -offset))
                        pips.add(Point3D(0.0, -1.0, 0.0))
                        pips.add(Point3D(offset, -1.0, offset))
                    }
                    4 -> {
                        pips.add(Point3D(-offset, -1.0, -offset))
                        pips.add(Point3D(offset, -1.0, -offset))
                        pips.add(Point3D(-offset, -1.0, offset))
                        pips.add(Point3D(offset, -1.0, offset))
                    }
                    5 -> {
                        pips.add(Point3D(-offset, -1.0, -offset))
                        pips.add(Point3D(offset, -1.0, -offset))
                        pips.add(Point3D(0.0, -1.0, 0.0))
                        pips.add(Point3D(-offset, -1.0, offset))
                        pips.add(Point3D(offset, -1.0, offset))
                    }
                    6 -> {
                        pips.add(Point3D(-offset, -1.0, -offset))
                        pips.add(Point3D(0.0, -1.0, -offset))
                        pips.add(Point3D(offset, -1.0, -offset))
                        pips.add(Point3D(-offset, -1.0, offset))
                        pips.add(Point3D(0.0, -1.0, offset))
                        pips.add(Point3D(offset, -1.0, offset))
                    }
                }
            }
        }
        
        // Scale pip positions by cube size
        return pips.map { Point3D(it.x * cubeSize, it.y * cubeSize, it.z * cubeSize) }
    }
    
    fun rollDice(finalValue: Int, intensity: Float = 1.0f, onComplete: () -> Unit) {
        if (isAnimating) return
        isAnimating = true

        // Target orientation for the final face (wrap once so we don't jump between equivalent angles)
        val (rawTargetX, rawTargetY, _) = orientationForValue(finalValue)
        val targetX = wrapAngleRad(rawTargetX)
        val targetY = wrapAngleRad(rawTargetY)

        val safeIntensity = intensity.coerceIn(0.65f, 1.85f)

        Log.d(
            logTag,
            "rollDice start value=$finalValue intensity=${"%.2f".format(safeIntensity)} " +
                "target=(x=${"%.2f".format(targetX)}, y=${"%.2f".format(targetY)}) " +
                "start=(x=${"%.2f".format(rotX)}, y=${"%.2f".format(rotY)}, z=${"%.2f".format(rotZ)})"
        )

        // Random final twist around the viewing axis feels more natural.
        // Wrap once so 3π/2 becomes -π/2 consistently (prevents end-of-animation snap).
        val targetZ = wrapAngleRad(listOf(0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2).random())

        // Initialize a tumble that decays, while we smoothly blend the displayed rotation to target.
        // Use a single direction for the whole roll so it never reverses during settling.
        val globalDir = if (Random.nextBoolean()) 1 else -1
        dirX = globalDir
        dirY = globalDir
        dirZ = globalDir

        omegaX = Random.nextDouble(6.0, 9.0) * globalDir * safeIntensity
        omegaY = Random.nextDouble(5.0, 8.0) * globalDir * safeIntensity
        omegaZ = Random.nextDouble(4.0, 7.0) * globalDir * safeIntensity
        lastAnimTimeMs = 0L

        tumbleX = rotX
        tumbleY = rotY
        tumbleZ = rotZ

        val durationMs = (2200L / safeIntensity.coerceAtLeast(0.85f)).toLong().coerceIn(1800L, 2600L)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()

            addUpdateListener { animator ->
                val f = (animator.animatedValue as Float).toDouble().coerceIn(0.0, 1.0)
                val nowMs = animator.currentPlayTime
                val dt = if (lastAnimTimeMs == 0L) 0.016 else ((nowMs - lastAnimTimeMs).toDouble() / 1000.0).coerceIn(0.0, 0.05)
                lastAnimTimeMs = nowMs

                if (dt > 0.0) {
                    // Simple deceleration - starts at 1.0, smoothly goes to 0
                    // Using cosine curve: starts high, ends at zero, always positive
                    val speed = 0.5 * (1.0 + cos(Math.PI * f))
                    
                    tumbleX += omegaX * dt * speed
                    tumbleY += omegaY * dt * speed
                    tumbleZ += omegaZ * dt * speed

                    tumbleX = wrapAngleRad(tumbleX)
                    tumbleY = wrapAngleRad(tumbleY)
                    tumbleZ = wrapAngleRad(tumbleZ)
                }
                
                // Gradual blend to target in final 35%
                val blendStart = 0.65
                val blend = if (f < blendStart) {
                    0.0
                } else {
                    val t = (f - blendStart) / (1.0 - blendStart)
                    t * t * (3.0 - 2.0 * t) // Smoothstep
                }
                
                rotX = lerpAngleRadDirectional(tumbleX, targetX, blend, dirX)
                rotY = lerpAngleRadDirectional(tumbleY, targetY, blend, dirY)
                rotZ = lerpAngleRadDirectional(tumbleZ, targetZ, blend, dirZ)

                invalidate()
            }

            doOnEnd {
                // Ensure final state is exact
                rotX = targetX
                rotY = targetY
                rotZ = targetZ
                
                omegaX = 0.0
                omegaY = 0.0
                omegaZ = 0.0
                lastAnimTimeMs = 0L
                isAnimating = false

                tumbleX = targetX
                tumbleY = targetY
                tumbleZ = targetZ

                val front = currentFrontFaceValue()
                Log.d(
                    logTag,
                    "rollDice end value=$finalValue front=$front final=(x=${"%.2f".format(rotX)}, y=${"%.2f".format(rotY)}, z=${"%.2f".format(rotZ)})"
                )

                onComplete()
                invalidate()
            }

            start()
        }
    }
    
    fun setValue(value: Int) {
        val (targetX, targetY, targetZ) = orientationForValue(value)
        rotX = targetX
        rotY = targetY
        rotZ = targetZ
        invalidate()
    }
    
    fun isRolling(): Boolean = isAnimating

    private fun currentFrontFaceValue(): Int {
        // Determine which face is most front-facing in the current orientation.
        // Our draw loop considers faces with normalZ < 0 as visible (camera looks down -Z).
        var bestValue = faces.first().value
        var bestNormalZ = Double.POSITIVE_INFINITY

        val transformed = vertices.map { v ->
            var p = Point3D(v.x * cubeSize, v.y * cubeSize, v.z * cubeSize)
            p = rotateX(p, rotX)
            p = rotateY(p, rotY)
            p = rotateZ(p, rotZ)
            p
        }

        for (face in faces) {
            val v1 = transformed[face.vertices[1]].subtract(transformed[face.vertices[0]])
            val v2 = transformed[face.vertices[2]].subtract(transformed[face.vertices[0]])
            val normalZ = v1.cross(v2).z
            if (normalZ < bestNormalZ) {
                bestNormalZ = normalZ
                bestValue = face.value
            }
        }

        return bestValue
    }
    
    private fun orientationForValue(value: Int): Triple<Double, Double, Double> {
        return when (value) {
            // IMPORTANT: With our current face winding + backface culling (normalZ < 0 visible),
            // the default visible face at (0,0,0) is value 6.
            6 -> Triple(0.0, 0.0, 0.0)
            1 -> Triple(0.0, Math.PI, 0.0)
            2 -> Triple(0.0, Math.PI / 2, 0.0)
            5 -> Triple(0.0, -Math.PI / 2, 0.0)
            3 -> Triple(Math.PI / 2, 0.0, 0.0)
            4 -> Triple(-Math.PI / 2, 0.0, 0.0)
            else -> Triple(0.0, 0.0, 0.0)
        }
    }
    
    // Helper classes
    private data class Point3D(val x: Double, val y: Double, val z: Double) {
        fun subtract(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
        fun cross(other: Point3D) = Point3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
        fun dot(other: Point3D) = x * other.x + y * other.y + z * other.z
        fun magnitude() = sqrt(x * x + y * y + z * z)
        fun normalize(): Point3D {
            val mag = magnitude()
            return if (mag > 0) Point3D(x / mag, y / mag, z / mag) else this
        }
    }
    
    private data class Point2D(val x: Float, val y: Float)
    private data class Face(val vertices: IntArray, val value: Int)
    private data class FaceData(val face: Face, val depth: Double, val normalZ: Double)
}
