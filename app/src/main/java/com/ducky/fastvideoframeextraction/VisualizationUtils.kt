/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package com.ducky.fastvideoframeextraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Join
import android.graphics.PointF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import com.ducky.fastvideoframeextraction.data.BodyPart
import com.ducky.fastvideoframeextraction.data.Person
import java.io.File
import kotlin.math.atan2
import kotlin.math.max

object VisualizationUtils {
    /** Radius of circle used to draw keypoints.  */
    private const val CIRCLE_RADIUS = 6f

    /** Width of line used to connected two keypoints.  */
    private const val LINE_WIDTH = 4f

    private const val LINE_ANG = 2f

    /** The text size of the person id that will be displayed when the tracker is available.  */
    private const val PERSON_ID_TEXT_SIZE = 30f

    /** Distance from person id to the nose keypoint.  */
    private const val PERSON_ID_MARGIN = 6f

    /** Pair of key-points to draw lines between.  */
    private val bodyJoints = listOf(
        Pair(BodyPart.NOSE, BodyPart.LEFT_EYE),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_EYE),
        Pair(BodyPart.LEFT_EYE, BodyPart.LEFT_EAR),
        Pair(BodyPart.RIGHT_EYE, BodyPart.RIGHT_EAR),
        Pair(BodyPart.NOSE, BodyPart.LEFT_SHOULDER),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
        Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
        Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
        Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
        Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
        Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    )


    private val triJoints: Map<String, List<BodyPart>>
        get() = mapOf(
            "RIGHT_KNEE" to listOf(BodyPart.RIGHT_HIP,BodyPart.RIGHT_KNEE,BodyPart.RIGHT_ANKLE),
            "LEFT_KNEE" to listOf(BodyPart.LEFT_HIP,BodyPart.LEFT_KNEE,BodyPart.LEFT_ANKLE),
            "LEFT_HIP" to listOf(BodyPart.LEFT_SHOULDER,BodyPart.LEFT_HIP,BodyPart.LEFT_KNEE),
            "RIGHT_HIP" to listOf(BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_HIP,BodyPart.RIGHT_KNEE),
            "RIGHT_SHOULDER" to listOf(BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_HIP),
            "LEFT_SHOULDER" to listOf(BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER,BodyPart.LEFT_HIP),
            "LEFT_ELBOW" to listOf(BodyPart.LEFT_WRIST,BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER),
            "RIGHT_ELBOW" to listOf(BodyPart.RIGHT_WRIST,BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER),
    
        )

    private fun points2D_to_angles(top: PointF,mid: PointF,bot: PointF) : Float {

        val ux = top.x-mid.x
        val uy = top.y-mid.y
        val vx = bot.x-mid.x
        val vy = bot.y-mid.y
        var angle = (atan2(uy,ux) - atan2(vy,vx)).toDouble()
        if (angle < 0){
            angle += Math.PI
        }else{
            if(angle>Math.PI){
                angle = 2*Math.PI - angle
            }
        }
        angle = angle*360/(2*Math.PI)
        return angle.toFloat()



    }
    fun p2a(top: PointF,mid: PointF,bot: PointF): Float{
        val ux = top.x-mid.x
        val uy = top.y-mid.y
        val vx = bot.x-mid.x
        val vy = bot.y-mid.y
        val dot = ux*vx + uy*vy
        val det = ux*vy-uy*vx
        var angle = atan2(-det, -dot) + Math.PI
        angle = angle*360/(2*Math.PI)
        return angle.toFloat()
    }


    fun estremi(scores: List<Pair<String,Person>>,joint: BodyPart):Float{
        var min : Person
        var max : Person
        var mid : Person
        var minIndx = 1
        var maxIndx = 0
        var midIndx = 0
        var minFrame = scores[1].first
        var maxFrame = ""
        var midFrame = ""
        var midBitmap : Bitmap?
        val maxBitmap : Bitmap?
        val minBitmap : Bitmap?
        // minFrame, maxFrame = estremi(scores, joint)
        if (scores.size !=0) {
            min = scores[0].second
            mid = scores[scores.size *3 / 10].second
            max = scores[scores.size *8 / 10].second
            maxIndx = scores.size *8 / 10
            midIndx = scores.size *3 / 10

            for (i in 1..scores.size - 1) {
                if (i%2==0 && (scores[i].second.score > min.score) && (i < scores.size/10)) {
                    min = scores[i].second

                    minIndx = i
                }
                if (i%2==0 && (scores[i].second.score > max.score )&& (i > scores.size *8/10) ) {
                    max = scores[i].second
                    maxIndx = i
                }
                if(i%2==0 && (scores[i].second.score > mid.score )&& (i >(scores.size *3 / 10)) && (i< (scores.size *5 / 10))){
                    mid = scores[i].second
                    midIndx = i
                }
            }
            minFrame = scores[minIndx].first
            maxFrame = scores[maxIndx].first
            midFrame = scores[midIndx].first
        }
        else return -1.0f
        val maxFile = File(maxFrame)
        val minFile = File(minFrame)
        val midFile = File(midFrame)
        if (maxFile.exists() && minFile.exists()) {
            maxBitmap = BitmapFactory.decodeFile(maxFile.absolutePath)
            minBitmap = BitmapFactory.decodeFile(minFile.absolutePath)
            midBitmap = BitmapFactory.decodeFile(midFile.absolutePath)
        }
        var joints = JointAngle[joint]
//        var angle_min =  points2D_to_angles(min.keyPoints[joints!![0]?.position].coordinate,
//            min.keyPoints[joints!![1]?.position].coordinate,
//            min.keyPoints[joints!![2]?.position].coordinate)
//        var angle_max =  points2D_to_angles(max.keyPoints[joints!![0]?.position].coordinate,
//            max.keyPoints[joints!![1]?.position].coordinate,
//            max.keyPoints[joints!![2]?.position].coordinate)
//        if (angle_max< angle_min){
//            angle_max += 180
//        }
        var angle_max2 = p2a(max.keyPoints[joints!![0]?.position].coordinate,
            max.keyPoints[joints!![1]?.position].coordinate,
            max.keyPoints[joints!![2]?.position].coordinate)
        var angle_min2=  p2a(min.keyPoints[joints!![0]?.position].coordinate,
            min.keyPoints[joints!![1]?.position].coordinate,
            min.keyPoints[joints!![2]?.position].coordinate)





        var ang1 = wrap_angle(min,mid,joint)
        var ang2 = wrap_angle(min,max,joint)

        var ang11 = wrap_angle360(min,mid,joint)
        var ang22 = wrap_angle360(min,max,joint)
        if(ang11>ang22){
            ang22 = 360-ang22
        }


//        var altroangolo = points2D_to_angles(min.keyPoints[joints!![0]?.position].coordinate,
//            min.keyPoints[joints!![1]?.position].coordinate ,
//            align( min.keyPoints[joints!![1]?.position].coordinate ,max.keyPoints[joints!![1]?.position].coordinate, max.keyPoints[joints!![0]?.position].coordinate)
//            )
//        var angolo_invert = points2D_to_angles(max.keyPoints[joints!![0]?.position].coordinate,
//            max.keyPoints[joints!![1]?.position].coordinate ,
//            align( max.keyPoints[joints!![1]?.position].coordinate ,min.keyPoints[joints!![1]?.position].coordinate, min.keyPoints[joints!![0]?.position].coordinate)
//        )

        return (ang22)


    }

    fun wrap_angle(posA : Person,posB : Person,joint: BodyPart): Float{
        var joints = JointAngle[joint]
        return points2D_to_angles(posA.keyPoints[joints!![0]?.position].coordinate,
            posA.keyPoints[joints!![1]?.position].coordinate ,
            align( posA.keyPoints[joints!![1]?.position].coordinate ,posB.keyPoints[joints!![1]?.position].coordinate, posB.keyPoints[joints!![0]?.position].coordinate)
        )
    }

    fun wrap_angle360(posA : Person,posB : Person,joint: BodyPart): Float{
        var joints = JointAngle[joint]
        return p2a(posA.keyPoints[joints!![0]?.position].coordinate,
            posA.keyPoints[joints!![1]?.position].coordinate ,
            align( posA.keyPoints[joints!![1]?.position].coordinate ,posB.keyPoints[joints!![1]?.position].coordinate, posB.keyPoints[joints!![0]?.position].coordinate)
        )
    }

    fun align(A: PointF,B:PointF, C : PointF):PointF{
        var Cx = (A.x - B.x)
        var Cy = (A.y - B.y)

        return PointF(C.x+Cx,C.y+Cy)
    }


    private val JointAngle = mapOf(
        BodyPart.RIGHT_KNEE to listOf(BodyPart.RIGHT_ANKLE,BodyPart.RIGHT_KNEE,BodyPart.RIGHT_HIP),
        BodyPart.LEFT_KNEE to listOf(BodyPart.LEFT_ANKLE,BodyPart.LEFT_KNEE,BodyPart.LEFT_HIP),
        BodyPart.LEFT_HIP to listOf(BodyPart.LEFT_KNEE,BodyPart.LEFT_HIP,BodyPart.LEFT_SHOULDER),
        BodyPart.RIGHT_HIP to listOf(BodyPart.RIGHT_KNEE,BodyPart.RIGHT_HIP,BodyPart.RIGHT_SHOULDER),
        BodyPart.LEFT_SHOULDER to listOf(BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER,BodyPart.LEFT_HIP),
        BodyPart.RIGHT_SHOULDER to listOf(BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_HIP),
        BodyPart.LEFT_ELBOW to listOf(BodyPart.LEFT_WRIST,BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER),
        BodyPart.RIGHT_ELBOW to listOf(BodyPart.RIGHT_WRIST,BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER)
    )

    private val Joints = listOf(
        listOf(BodyPart.RIGHT_HIP,BodyPart.RIGHT_KNEE,BodyPart.RIGHT_ANKLE),
        listOf(BodyPart.LEFT_HIP,BodyPart.LEFT_KNEE,BodyPart.LEFT_ANKLE),
        listOf(BodyPart.LEFT_SHOULDER,BodyPart.LEFT_HIP,BodyPart.LEFT_KNEE),
        listOf(BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_HIP,BodyPart.RIGHT_KNEE),
        listOf(BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER,BodyPart.LEFT_HIP),
        listOf(BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_HIP),
        listOf(BodyPart.LEFT_WRIST,BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER),
        listOf(BodyPart.RIGHT_WRIST,BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER)
    )

    // Draw line and point indicate body pose
    @RequiresApi(Build.VERSION_CODES.O)
    fun drawBodyKeypoints(
        input: Bitmap,
        persons: List<Person>,
        isTrackerEnabled: Boolean = true,

    ): Bitmap {
        var angJoints: MutableList<Pair<Float,List<BodyPart>>> = arrayListOf()   //<Pair<Float,List<BodyPart>>>
        val paintCircle = Paint().apply {
            strokeWidth = CIRCLE_RADIUS
            color = Color.RED
            style = Paint.Style.FILL
        }

        val paintCircleRIGHT = Paint().apply {
            strokeWidth = CIRCLE_RADIUS
            color = Color.GREEN
            style = Paint.Style.FILL
        }

        val paintLine = Paint().apply {
            strokeWidth = LINE_WIDTH
            color = Color.RED
            style = Paint.Style.STROKE
        }

        val paintLineAng = Paint().apply {
            strokeWidth = LINE_ANG
            color = Color.LTGRAY
            style = Paint.Style.STROKE
        }

        val paintText = Paint().apply {
            textSize = PERSON_ID_TEXT_SIZE
            color = Color.BLUE
            textAlign = Paint.Align.LEFT
        }
        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        val outpu = createBitmap(input.width,input.height,Bitmap.Config.RGBA_F16)
        val output = input.copy(Bitmap.Config.RGBA_F16, true)
        val originalSizeCanvas = Canvas(outpu)
        persons.forEach { person ->
            // draw person id if tracker is enable
            if (isTrackerEnabled) {
                person.boundingBox?.let {
                    val personIdX = max(0f, it.left)
                    val personIdY = max(0f, it.top)

                    originalSizeCanvas.drawText(
                        person.id.toString(),
                        personIdX,
                        personIdY - PERSON_ID_MARGIN,
                        paintText
                    )
                    originalSizeCanvas.drawRect(it, paintLine)
                }
            }
            bodyJoints.forEach {
                val pointA = person.keyPoints[it.first.position].coordinate

                val pointB = person.keyPoints[it.second.position].coordinate
                originalSizeCanvas.drawLine(pointA.x, pointA.y, pointB.x, pointB.y, paintLine)
            }

            Joints.forEach{
                val a = 1
                val jointTop = person.keyPoints[it[0].position].coordinate
                val jointMid = person.keyPoints[it[1].position].coordinate
                val jointBot = person.keyPoints[it[2].position].coordinate
                //originalSizeCanvas.drawLine(jointTop.x, jointTop.y, jointBot.x, jointBot.y, paintLineAng)
                val angle = (points2D_to_angles(jointTop,jointMid,jointBot))

                angJoints.add(Pair(angle,it))

            }

            person.keyPoints.forEach { point ->
                if("LEFT" in point.bodyPart.name){
                    originalSizeCanvas.drawCircle(
                        point.coordinate.x,
                        point.coordinate.y,
                        CIRCLE_RADIUS,
                        paintCircle
                    )
                 }else{
                    originalSizeCanvas.drawCircle(
                        point.coordinate.x,
                        point.coordinate.y,
                        CIRCLE_RADIUS,
                        paintCircleRIGHT
                    )
                 }

            }

        }
        val lastInferenceTimeNanos =
            SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos
        Log.d("DRAWING TIME","Drawing time : " +(lastInferenceTimeNanos.toFloat()/1000000).toString() )
        return outpu
    }



}
