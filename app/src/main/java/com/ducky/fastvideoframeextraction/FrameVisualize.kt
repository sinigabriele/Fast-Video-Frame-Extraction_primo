package com.ducky.fastvideoframeextraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ducky.fastvideoframeextraction.data.Device
import com.ducky.fastvideoframeextraction.data.KeyPoint
import com.ducky.fastvideoframeextraction.data.Person
import com.ducky.fastvideoframeextraction.ml.ModelType
import com.ducky.fastvideoframeextraction.ml.MoveNet
import com.ducky.fastvideoframeextraction.ml.PoseDetector

class FrameVisualize : AppCompatActivity() {


    val imageAdapter = MainActivity.DataHolder.imageAdapter
    val imagePaths = MainActivity.DataHolder.imagePaths
    val detector = MainActivity.DataHolder.detector
    val scores = MainActivity.DataHolder.scores
    val selectedJointId = MainActivity.DataHolder.selectedJointId
    private lateinit var poseOne: Bitmap
    private lateinit var poseTwo: Bitmap
    private lateinit var angleComputed : String
    val device = Device.CPU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.frame_visualize)




        val imageView1: ImageView = findViewById(R.id.imageView1)
        val imageView2: ImageView = findViewById(R.id.imageView2)
        val textView: TextView = findViewById(R.id.textViewTop)

        compute()

        // Imposta le immagini e il testo come necessario
        imageView1.setImageBitmap(poseOne)
        imageView2.setImageBitmap(poseTwo)
        textView.text = "The angle of "+VisualizationUtils.IdToJoint[selectedJointId].toString() +" is " + angleComputed + " degrees"

    }



    private fun compute() {
        var selected = imageAdapter?.selectedItems
        var bitmaps = ArrayList<Bitmap>()
        var twoPerson = ArrayList<Person>()

        var midInd = selected!!.sum() / 2
        selected.add(midInd)

        for (item in selected) {
            var bitmap = BitmapFactory.decodeFile(imagePaths[item].path)
            if(detector!=null) {
                val (processed, score) = processPhoto(bitmap, detector)
                scores.add(Pair(imagePaths[item].path,score) as Pair<String, Person>)
                processed?.let { bitmaps.add(it) }
            }
        }


        poseOne = bitmaps[0]
        poseTwo = bitmaps[1]
        val res= VisualizationUtils.posiComp(scores,selectedJointId)
        angleComputed = res.toString()
    }



    fun processPhoto(photo: Bitmap?, detector: PoseDetector) : Pair<Bitmap?, Person?> {
        val persons = mutableListOf<Person>()
        val pers_null = mutableListOf<KeyPoint>()
        photo?.let {
            detector.estimatePoses(it).let {
                if (it != null) {
                    persons.addAll(it)
                }
            }
        }
        if (persons.size==1) {      //era !=0
            Log.d("POSE DETECTOIN","POSE detected")
            val outputBitmap = photo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VisualizationUtils.drawBodyKeypoints(
                        it,
                        persons.filter { it.score > .2f }, //      Companion.MIN_CONFIDENCE },
                        true,
                        showBG = false
                    )
                } else {
                    TODO("VERSION.SDK_INT < O")
                }
            }
            return Pair(outputBitmap,persons[0])
        }
        return Pair(photo,Person(-1,pers_null.toList(),null, 0f))


    }

}