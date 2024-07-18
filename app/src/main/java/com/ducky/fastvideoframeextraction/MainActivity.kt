package com.ducky.fastvideoframeextraction

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.*
import com.ducky.fastvideoframeextraction.data.BodyPart
import com.ducky.fastvideoframeextraction.data.Device
import com.ducky.fastvideoframeextraction.data.KeyPoint
import com.ducky.fastvideoframeextraction.data.Person
import com.ducky.fastvideoframeextraction.decoder.Frame
import com.ducky.fastvideoframeextraction.decoder.FrameExtractor
import com.ducky.fastvideoframeextraction.decoder.IVideoFrameExtractor
import com.ducky.fastvideoframeextraction.ml.ModelType
import com.ducky.fastvideoframeextraction.ml.MoveNet
import com.ducky.fastvideoframeextraction.ml.PoseDetector
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), IVideoFrameExtractor {

    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var imageAdapter: ImageAdapter
    private var imagePaths = ArrayList<Uri>()
    private var titles: ArrayList<String> = ArrayList()
    private lateinit var videoInputFile: File

    var totalSavingTimeMS: Long = 0
    lateinit var infoTextView: TextView

    private lateinit var detector : PoseDetector
    private var device = Device.CPU
    private var scores = mutableListOf<Pair<String,Person>>()

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val dataUri = data?.data
            if (dataUri != null) {
                val uriPathHelper = URIPathHelper()
                val videoInputPath = uriPathHelper.getPath(this, dataUri).toString()
                videoInputFile = File(videoInputPath)

                val frameExtractor = FrameExtractor(this)
                executorService.execute {
                    try {

                        frameExtractor.extractFrames(videoInputFile.absolutePath)
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                        this.runOnUiThread {
                            Toast.makeText(this, "Failed to extract frames", Toast.LENGTH_SHORT).show()
                        }
                    }
                }


            } else {
                Toast.makeText(this, "Video input error!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val videoSelectBt: Button = this.findViewById(R.id.select_bt)
        infoTextView = this.findViewById(R.id.info_tv)

        videoSelectBt.setOnClickListener {
            // Clear all previous images path and title
            imagePaths.clear()
            titles.clear()
            totalSavingTimeMS = 0

            openGalleryForVideo()
        }


        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }
        detector = MoveNet.create(this, device, ModelType.Lightning)
    }

    private fun openGalleryForVideo() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_PICK
        resultLauncher.launch(intent)
    }

    private fun updateRecycleView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        if (recyclerView != null) {
            val layoutManager = StaggeredGridLayoutManager(3, OrientationHelper.VERTICAL)
            layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            recyclerView.layoutManager = layoutManager
            imageAdapter = ImageAdapter(this, imagePaths, titles)
            recyclerView.adapter = imageAdapter
            recyclerView.itemAnimator = DefaultItemAnimator()

            val dividerItemDecorationVertical = DividerItemDecoration(
                recyclerView.context,
                LinearLayout.HORIZONTAL
            )

            val dividerItemDecorationHorizontal = DividerItemDecoration(
                recyclerView.context,
                LinearLayout.VERTICAL
            )
            recyclerView.addItemDecoration(dividerItemDecorationVertical)
            recyclerView.addItemDecoration(dividerItemDecorationHorizontal)
        }
    }

    private fun getRequiredPermissions(): Array<String?> {
        return try {
            val info = this.packageManager
                .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.isNotEmpty()) {
                ps
            } else {
                arrayOfNulls(0)
            }
        } catch (e: Exception) {
            arrayOfNulls(0)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val allNeededPermissions = ArrayList<String>()
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    allNeededPermissions.add(permission)
                }
            }
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
            )
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUESTS = 1
    }

    override fun onCurrentFrameExtracted(currentFrame: Frame) {

        val startSavingTime = System.currentTimeMillis()
        // 1. Convert frame byte buffer to bitmap
        val imageBitmap = Utils.fromBufferToBitmap(currentFrame.byteBuffer, currentFrame.width, currentFrame.height)

        // 2. Get the frame file in app external file directory
//        val allFrameFileFolder = File(this.getExternalFilesDir(null), UUID.randomUUID().toString())
//        if (!allFrameFileFolder.isDirectory) {
//            allFrameFileFolder.mkdirs()
//        }
        var path  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()
        path =  path + "/app"
        val frameFile = File(path, videoInputFile.nameWithoutExtension +"frame_num_${currentFrame.timestamp.toString().padStart(10, '0')}.jpeg")
        //imageBitmap?.let{detector.estimatePoses(it)}
        if(detector!=null) {
            val (processed, score) = processPhoto(imageBitmap?.let { verticalFlip(it) }, detector)
            processed?.let {
                val savedFile = Utils.saveImageToFile(it, frameFile)
                savedFile?.let {
                    imagePaths.add(savedFile.toUri())
                    titles.add("${currentFrame.position} (${currentFrame.timestamp})")
                }
            }

            scores.add(Pair(frameFile.absolutePath,score) as Pair<String, Person>)
        }
        // 3. Save current frame to storage


        totalSavingTimeMS += System.currentTimeMillis() - startSavingTime

        this.runOnUiThread {
            infoTextView.text = "Extract ${currentFrame.position} frames"
        }
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
                VisualizationUtils.drawBodyKeypoints(
                    it,
                    persons.filter { it.score > .2f }, //      Companion.MIN_CONFIDENCE },
                    true
                )
            }
            return Pair(outputBitmap,persons[0])
        }
        return Pair(photo,Person(-1,pers_null.toList(),null, 0f))


    }

    fun verticalFlip(source: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(1.0f, -1.0f)
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true)
    }

    @SuppressLint("SetTextI18n")
    override fun onAllFrameExtracted(processedFrameCount: Int, processedTimeMs: Long) {
        Log.d(TAG, "Save: $processedFrameCount frames in: $processedTimeMs ms.")
        var res = VisualizationUtils.estremi(scores, BodyPart.RIGHT_HIP)
        this.runOnUiThread {
            updateRecycleView()
            infoTextView.text = "Extract $processedFrameCount frames took $processedTimeMs ms| Saving took: $totalSavingTimeMS ms"
        }
    }
}