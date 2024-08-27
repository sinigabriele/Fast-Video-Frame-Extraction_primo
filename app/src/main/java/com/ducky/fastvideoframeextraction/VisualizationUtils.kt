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
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import com.ducky.fastvideoframeextraction.Utils.transformVectors
import com.ducky.fastvideoframeextraction.data.BodyPart
import com.ducky.fastvideoframeextraction.data.KeyPoint
import com.ducky.fastvideoframeextraction.data.Person
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue
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
            "RIGHT_SHOULDER" to listOf(BodyPart.RIGHT_HIP,BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_ELBOW),
            "LEFT_SHOULDER" to listOf(BodyPart.LEFT_HIP,BodyPart.LEFT_SHOULDER,BodyPart.LEFT_ELBOW),
//            "LEFT_ELBOW" to listOf(BodyPart.LEFT_WRIST,BodyPart.LEFT_ELBOW,BodyPart.LEFT_SHOULDER),
//            "RIGHT_ELBOW" to listOf(BodyPart.RIGHT_WRIST,BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_SHOULDER),
            "LEFT_ELBOW" to listOf(BodyPart.LEFT_SHOULDER,BodyPart.LEFT_ELBOW,BodyPart.LEFT_WRIST),
            "RIGHT_ELBOW" to listOf(BodyPart.RIGHT_SHOULDER,BodyPart.RIGHT_ELBOW,BodyPart.RIGHT_WRIST)

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

    fun posiComp(scores: List<Pair<String,Person>>,joint: Int):Int{
        val joint = IdToJoint[joint]
        var min = scores[0].second
        var max = scores[1].second
        var mid = scores[2].second
        var minIndx = 0
        var maxIndx = 1
        var midIndx = 2

        var a = JointConnected(joint)
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

        return (ang22.toInt())


    }

//    fun estremi(scores: List<Pair<String,Person>>,joint: Int):Float{
//        val joint = IdToJoint[joint]
//        var min : Person
//        var max : Person
//        var mid : Person
//        var minIndx = 1
//        var maxIndx = 0
//        var midIndx = 0
//        var minFrame = scores[1].first
//        var maxFrame = ""
//        var midFrame = ""
//        var midBitmap : Bitmap?
//        val maxBitmap : Bitmap?
//        val minBitmap : Bitmap?
//        // minFrame, maxFrame = estremi(scores, joint)
//        if (scores.size !=0) {
//            min = scores[0].second
//            mid = scores[scores.size *3 / 10].second
//            max = scores[scores.size *8 / 10].second
//            maxIndx = scores.size *8 / 10
//            midIndx = scores.size *3 / 10
//
//            for (i in 1..scores.size - 1) {
//                if (i%2==0 && (scores[i].second.score > min.score) && (i < scores.size/10)) {
//                    min = scores[i].second
//
//                    minIndx = i
//                }
//                if (i%2==0 && (scores[i].second.score > max.score )&& (i > scores.size *8/10) ) {
//                    max = scores[i].second
//                    maxIndx = i
//                }
//                if(i%2==0 && (scores[i].second.score > mid.score )&& (i >(scores.size *3 / 10)) && (i< (scores.size *5 / 10))){
//                    mid = scores[i].second
//                    midIndx = i
//                }
//            }
//            minFrame = scores[minIndx].first
//            maxFrame = scores[maxIndx].first
//            midFrame = scores[midIndx].first
//        }
//        else return -1.0f
//        val maxFile = File(maxFrame)
//        val minFile = File(minFrame)
//        val midFile = File(midFrame)
//        if (maxFile.exists() && minFile.exists()) {
//            maxBitmap = BitmapFactory.decodeFile(maxFile.absolutePath)
//            minBitmap = BitmapFactory.decodeFile(minFile.absolutePath)
//            midBitmap = BitmapFactory.decodeFile(midFile.absolutePath)
//        }
//        var joints = JointAngle[joint]
////        var angle_min =  points2D_to_angles(min.keyPoints[joints!![0]?.position].coordinate,
////            min.keyPoints[joints!![1]?.position].coordinate,
////            min.keyPoints[joints!![2]?.position].coordinate)
////        var angle_max =  points2D_to_angles(max.keyPoints[joints!![0]?.position].coordinate,
////            max.keyPoints[joints!![1]?.position].coordinate,
////            max.keyPoints[joints!![2]?.position].coordinate)
////        if (angle_max< angle_min){
////            angle_max += 180
////        }
//        var angle_max2 = p2a(max.keyPoints[joints!![0]?.position].coordinate,
//            max.keyPoints[joints!![1]?.position].coordinate,
//            max.keyPoints[joints!![2]?.position].coordinate)
//        var angle_min2=  p2a(min.keyPoints[joints!![0]?.position].coordinate,
//            min.keyPoints[joints!![1]?.position].coordinate,
//            min.keyPoints[joints!![2]?.position].coordinate)
//
//
//
//
//
//        var ang1 = wrap_angle(min,mid,joint)
//        var ang2 = wrap_angle(min,max,joint)
//
//        var ang11 = wrap_angle360(min,mid,joint)
//        var ang22 = wrap_angle360(min,max,joint)
//        if(ang11>ang22){
//            ang22 = 360-ang22
//        }
//
//
////        var altroangolo = points2D_to_angles(min.keyPoints[joints!![0]?.position].coordinate,
////            min.keyPoints[joints!![1]?.position].coordinate ,
////            align( min.keyPoints[joints!![1]?.position].coordinate ,max.keyPoints[joints!![1]?.position].coordinate, max.keyPoints[joints!![0]?.position].coordinate)
////            )
////        var angolo_invert = points2D_to_angles(max.keyPoints[joints!![0]?.position].coordinate,
////            max.keyPoints[joints!![1]?.position].coordinate ,
////            align( max.keyPoints[joints!![1]?.position].coordinate ,min.keyPoints[joints!![1]?.position].coordinate, min.keyPoints[joints!![0]?.position].coordinate)
////        )
//
//        return (ang22)
//
//
//    }

    fun wrap_angle(posA : Person,posB : Person,joint: BodyPart?): Float{
        var joints = JointAngle[joint]
        return points2D_to_angles(posA.keyPoints[joints!![0]?.position].coordinate,
            posA.keyPoints[joints!![1]?.position].coordinate ,
            align( posA.keyPoints[joints!![1]?.position].coordinate ,posB.keyPoints[joints!![1]?.position].coordinate, posB.keyPoints[joints!![0]?.position].coordinate)
        )
    }

    fun wrap_angle360(posA : Person,posB : Person,joint: BodyPart?): Float{
        var joints = JointAngle[joint]
        var ang =  p2a(posA.keyPoints[joints!![0]?.position].coordinate,
            posA.keyPoints[joints!![1]?.position].coordinate ,
            align( posA.keyPoints[joints!![1]?.position].coordinate ,posB.keyPoints[joints!![1]?.position].coordinate, posB.keyPoints[joints!![0]?.position].coordinate)
        )
        var ang2 = rototodo(posA ,posB, joint)
        return ang2
    }


    fun rototodo(posA : Person,posB : Person,joint: BodyPart?): Float {
        val joints = JointAngle[joint]
        val p0 = posA.keyPoints[joints!![0].position].coordinate
        val p1 = posA.keyPoints[joints[2].position].coordinate
        val p2 = posA.keyPoints[joints[1].position].coordinate
        val q1 = posB.keyPoints[joints[2].position].coordinate
        val q2 = posB.keyPoints[joints[1].position].coordinate

        val r2 = posB.keyPoints[joints[0].position].coordinate

        val (new_q1, new_q2, new_r2) = transformVectors(p1, p2,q1,q2,q2,r2)
        posB.keyPoints[joints[0].position].coordinate = new_r2
        posB.keyPoints[joints[1].position].coordinate = new_q2
        return p2a(p0,
            p2 ,
           new_r2
        )
    }


    fun align(A: PointF,B:PointF, C : PointF):PointF{
        var Cx = (A.x - B.x)
        var Cy = (A.y - B.y)

        return PointF(C.x+Cx,C.y+Cy)
    }

    fun traslation(A: PointF,B:PointF):Pair<Float,Float>{
        var Cx = (A.x - B.x)
        var Cy = (A.y - B.y)

        return Pair(Cx,Cy)
    }
    fun traslateP(pair : Pair<Float,Float>, point : PointF): PointF{
        point.x += pair.first
        point.y += pair.second
        return point
    }

     val IdToJoint = mapOf(
        0 to BodyPart.LEFT_KNEE,
        1 to BodyPart.LEFT_HIP,
        2 to BodyPart.LEFT_SHOULDER,
        3 to BodyPart.LEFT_ELBOW,
        4 to BodyPart.RIGHT_KNEE ,
        5 to BodyPart.RIGHT_HIP,
        6 to BodyPart.RIGHT_SHOULDER,
        7 to BodyPart.RIGHT_ELBOW
    )


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
        showBG: Boolean = false

    ): Bitmap {
        var angJoints: MutableList<Pair<Float,List<BodyPart>>> = arrayListOf()   //<Pair<Float,List<BodyPart>>>
        var outpu = input.copy(Bitmap.Config.RGBA_F16, true)
        if (!showBG) {
             outpu = createBitmap(input.width,input.height,Bitmap.Config.RGBA_F16)
        }
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

    // Draw line and point indicate body pose
    @RequiresApi(Build.VERSION_CODES.O)
    fun drawBodyKeypointsSOVRA(
        inputPair: Pair<Bitmap,Bitmap>,
        persons: List<Person>,
        selectedJoint: Int,
        isTrackerEnabled: Boolean = true,
        showBG: Boolean = false,
        angle: Float

    ): Bitmap {
        var input = inputPair.first
        var second = inputPair.second

        var primaP = persons[0]
        var secondP = persons[1]
        var persons = arrayListOf<Person>()
        persons.add(primaP)
       // var angJoints: MutableList<Pair<Float,List<BodyPart>>> = arrayListOf()   //<Pair<Float,List<BodyPart>>>

        val joint = IdToJoint[selectedJoint]
        var traslation = traslation(primaP.keyPoints[joint?.position!!].coordinate, secondP.keyPoints[joint?.position!!].coordinate) //calcolo la traslazione detra l'articolazione di interesse delle due poszioni
        val jointToTraslate = JointConnected(joint)

        var outpu = input.copy(Bitmap.Config.RGBA_F16, true)
        if (!showBG) {
            outpu = createBitmap(input.width,input.height,Bitmap.Config.RGBA_F16)
        }

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
        val paintLineSovra = Paint().apply {
            strokeWidth = LINE_WIDTH
            color = Color.BLUE
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

        val traslPoints = arrayListOf<KeyPoint>()
        for(j in jointToTraslate){
            val point = secondP.keyPoints[j.position]
//            point.coordinate.x += traslation.first
//            point.coordinate.y += traslation.second
            traslPoints.add(point)
        }

        var originalSizeCanvas = Canvas(outpu)
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
            val bodyPartList = traslPoints.map { keyPoint -> keyPoint.bodyPart }


            bodyJoints.forEach {
                try{
                    val firstPartMatches = bodyPartList.contains(it.first)
                    val secondPartMatches = bodyPartList.contains(it.second)

                    if (firstPartMatches && secondPartMatches) {
                        var pointA = PointF()
                        var pointB = PointF()
                        for(j in traslPoints){
                            if (j.bodyPart == it.first) {
                                pointA = j.coordinate
                            }
                            if (j.bodyPart == it.second) {
                                pointB = j.coordinate
                            }
                        }


                        originalSizeCanvas.drawLine(pointA.x, pointA.y, pointB.x, pointB.y, paintLineSovra)
                    }

                }catch(e: IOException){
                    Log.d("SOVRA DRAW","ERRORE nella traslazione dei punti")
                }finally{
                    Log.d("SOVRA DRAW","ERRORE nella traslazione dei punti")
                }

            }
            traslPoints.forEach { point ->
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

            originalSizeCanvas = drawSemiCircle(originalSizeCanvas,
                primaP.keyPoints[joint.position].coordinate,
                primaP.keyPoints[triJoints[joint.name]?.get(2)!!.position].coordinate,
                traslateP(traslation,secondP.keyPoints[triJoints[joint.name]?.get(2)!!.position].coordinate),
                angle
                )



//            Joints.forEach{
//                val a = 1
//                val jointTop = person.keyPoints[it[0].position].coordinate
//                val jointMid = person.keyPoints[it[1].position].coordinate
//                val jointBot = person.keyPoints[it[2].position].coordinate
//                //originalSizeCanvas.drawLine(jointTop.x, jointTop.y, jointBot.x, jointBot.y, paintLineAng)
//                val angle = (points2D_to_angles(jointTop,jointMid,jointBot))
//
//                angJoints.add(Pair(angle,it))
//
//            }

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

    fun drawSemiCircle(canvas: Canvas, C: PointF, P1: PointF, P2: PointF, angle: Float): Canvas {
        val cx = C.x
        val cy = C.y
        val x1 = P1.x
        val y1 = P1.y
        val x2 = P2.x
        val y2 = P2.y

        // Create a paint object to define the style of the drawing
        val paint = Paint()
        paint.color = Color.argb(128, 0, 102, 102) // Set the color you want
        paint.style = Paint.Style.FILL_AND_STROKE // Define if the style is fill or stroke
        paint.strokeWidth = 5f // Set the stroke width

        // Calculate the radius as the distance from the center to one of the points
        val radius = Math.hypot((cx - x1).toDouble(), (cy - y1).toDouble()).toFloat()/2

        // Calculate the bounding rectangle for the circle
        val rectF = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Determine the start and sweep angle for the arc
        val startAngle = Math.toDegrees(Math.atan2((y1 - cy).toDouble(), (x1 - cx).toDouble())).toFloat()
        val endAngle = Math.toDegrees(Math.atan2((y2 - cy).toDouble(), (x2 - cx).toDouble())).toFloat()
        var sweepAngle = endAngle - startAngle// if (endAngle >= startAngle)  360f + endAngle - startAngle else  endAngle - startAngle
        if((sweepAngle.absoluteValue - angle)>(360f+sweepAngle - angle))    {                                         //(sweepAngle.absoluteValue > angle +10f ||sweepAngle.absoluteValue < angle - 10f ){
            sweepAngle+=360f
        }else{
            sweepAngle = if(sweepAngle>0) angle else -angle
        }
        // Draw the arc (semicircle)
        canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)

        return canvas
    }


   fun JointConnected(bodyPart: BodyPart?):ArrayList<BodyPart>{
       var jointList = ArrayList<BodyPart>()
       var addedJoint = bodyPart
       if (addedJoint != null) {
           jointList.add(addedJoint)
       }
       var test = true

       try {
           while (test) {
               if (addedJoint != null) {
                   addedJoint = triJoints[addedJoint.name]!![2]
               }
               addedJoint?.let { jointList.add(it) }
           }
       }catch (e: IOException) {
           test= false

       }finally {
           return jointList
       }

       return jointList
   }

}
