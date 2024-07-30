package com.ducky.fastvideoframeextraction

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.recyclerview.widget.*
import com.ducky.fastvideoframeextraction.data.Device
import com.ducky.fastvideoframeextraction.data.KeyPoint
import com.ducky.fastvideoframeextraction.data.Person
import com.ducky.fastvideoframeextraction.decoder.Frame
import com.ducky.fastvideoframeextraction.decoder.FrameExtractor
import com.ducky.fastvideoframeextraction.decoder.IVideoFrameExtractor
import com.ducky.fastvideoframeextraction.ml.ModelType
import com.ducky.fastvideoframeextraction.ml.MoveNet
import com.ducky.fastvideoframeextraction.ml.PoseDetector
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), IVideoFrameExtractor , ImageAdapter.OnItemClickListener{





    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()


    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private val imagePaths = arrayListOf<Uri>()  // Add your image URIs here
    private val titles = arrayListOf<String>()   // Add your titles here

    private lateinit var videoInputFile: File

    private var selectedJoint: String? = null
    private var selectedJointId: Int = 0
//    private val
    private val PERMISSION_CODE = 1000
    private val VIDEO_CAPTURE_CODE = 1001

    var vFilename: String = ""
    val path  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath()  + File.separator
    //val root =  Environment.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.path+File.separator+"HofitApp"+File.separator
    val p = Environment.DIRECTORY_MOVIES + File.separator
   // private val REQUEST_VIDEO_CAPTURE = 1
    private lateinit var videoUri: Uri



    var totalSavingTimeMS: Long = 0
    lateinit var infoTextView: TextView
    lateinit var angleTextView: TextView

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
        // get reference to the string array that we just created
        val joint = resources.getStringArray(R.array.Joints)
        // create an array adapter and pass the required parameter
        // in our case pass the context, drop down layout , and array.
        val arrayAdapter = ArrayAdapter(this, R.layout.dropdown_item, joint)
        // get reference to the autocomplete text view
        val autocompleteTV = findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView)
        // set adapter to the autocomplete tv to the arrayAdapter
        autocompleteTV.setAdapter(arrayAdapter)

        // Imposta un listener per rilevare quando viene selezionato un elemento
        autocompleteTV.setOnItemClickListener { parent, view, position, id ->
            // Aggiorna la variabile con il valore selezionato
            selectedJoint = parent.getItemAtPosition(position) as String
            selectedJointId = position
            // Puoi anche fare altre operazioni con il valore selezionato qui
            // Ad esempio, stampare il valore selezionato nel log
            println("Selected joint: $selectedJoint")
        }

        val videoSelectBt: Button = this.findViewById(R.id.select_bt)
        infoTextView = this.findViewById(R.id.info_tv)
        angleTextView = findViewById<TextView>(R.id.text_view)
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


        val btn_takephoto_first: Button = this.findViewById(R.id.take_first_photo_bt)
        btn_takephoto_first.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                openCamera()
            } else {
                Toast.makeText(this,"Sorry you're version android is not support, Min Android 6.0 (Marsmallow)", Toast.LENGTH_LONG).show()
            }
        }

        val btn_takephoto_second: Button = this.findViewById(R.id.take_second_photo_bt)
        btn_takephoto_second.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                compute()
            } else {
                Toast.makeText(this,"Sorry you're version android is not support, Min Android 6.0 (Marsmallow)", Toast.LENGTH_LONG).show()
            }
        }
        updateRecyclerView()
    }


    private fun compute(): Float {
        var selected = imageAdapter.selectedItems
        var midInd = selected.sum() / 2
        selected.add(midInd)
        for (item in selected) {
            var bitmap = BitmapFactory.decodeFile(imagePaths[item].path)
            if(detector!=null) {
                val (processed, score) = processPhoto(bitmap, detector)
                scores.add(Pair(imagePaths[item].path,score) as Pair<String, Person>)
            }

        }
        val res= VisualizationUtils.posiComp(scores,selectedJointId)
        return res
    }

    private fun openGalleryForVideo() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_PICK
        resultLauncher.launch(intent)
    }


    private fun updateRecyclerView() {
        recyclerView = findViewById(R.id.recyclerview)
        val layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        recyclerView.layoutManager = layoutManager
        imageAdapter = ImageAdapter(this, imagePaths, titles, this)
        recyclerView.adapter = imageAdapter
        recyclerView.itemAnimator = DefaultItemAnimator()

        val dividerItemDecorationVertical = DividerItemDecoration(recyclerView.context, LinearLayout.HORIZONTAL)
        val dividerItemDecorationHorizontal = DividerItemDecoration(recyclerView.context, LinearLayout.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecorationVertical)
        recyclerView.addItemDecoration(dividerItemDecorationHorizontal)
    }

    override fun onItemClick(selectedIds: Set<Int>) {
        // Handle the selected item IDs
        Toast.makeText(this, "Selected IDs: $selectedIds", Toast.LENGTH_SHORT).show()
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
        var imageBitmap = Utils.fromBufferToBitmap(currentFrame.byteBuffer, currentFrame.width, currentFrame.height)

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
            //val (processed, score) = processPhoto(imageBitmap?.let { verticalFlip(it) }, detector)
            imageBitmap = imageBitmap?.let { verticalFlip(it) }
            imageBitmap?.let {
                val savedFile = Utils.saveImageToFile(it, frameFile)
                savedFile?.let {
                    imagePaths.add(savedFile.toUri())
                    titles.add("${currentFrame.position} (${currentFrame.timestamp})")
                }
            }

            //scores.add(Pair(frameFile.absolutePath,score) as Pair<String, Person>)
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
        //var res = VisualizationUtils.estremi(scores,selectedJointId)
        //angleTextView.setText("Joint Angle: " + res.toString())

        this.runOnUiThread {
            updateRecyclerView()
            infoTextView.text = "Extract $processedFrameCount frames took $processedTimeMs ms| Saving took: $totalSavingTimeMS ms"
        }
    }

//    private fun openCamera() {
//        val cameraIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
//        if (cameraIntent.resolveActivity(packageManager) != null) {
//            var videoFile: File? = null
//            try {
//                videoFile = createVideoFile()
//            } catch (ex: IOException) {
//                ex.printStackTrace()
//                Toast.makeText(this, "Error creating video file", Toast.LENGTH_SHORT).show()
//            }
//
//            if (videoFile != null) {
//                videoUri = FileProvider.getUriForFile(
//                    this,
//                    applicationContext.packageName + ".fileprovider", videoFile
//                )
//                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
//                startActivityForResult(cameraIntent, VIDEO_CAPTURE_CODE)
//            }
//        }
//    }

    private fun openCamera() {
        //val values = ContentValues()
//        values.put(MediaStore.Images.Media.TITLE, "New Picture")
//        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")

        //camera intent
        val cameraIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

        // set filename
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        vFilename = "VID_" + timeStamp + ".mp4"


        // set direcory folder
        val file = File(path, vFilename);
        //val file = createVideoFile()
        val video_uri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", file)

        //cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, video_uri)
        startActivityForResult(cameraIntent, VIDEO_CAPTURE_CODE)


//        val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
//        if (videoIntent.resolveActivity(packageManager) != null) {
//            val videoFile: File? = try {
//                createVideoFile()
//            } catch (ex: IOException) {
//                // Handle error
//                null
//            }
//            videoFile?.also {
//                videoUri = FileProvider.getUriForFile(
//                    this,
//                    "${BuildConfig.APPLICATION_ID}.provider",
//                    it
//                )
//                videoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
//                startActivityForResult(videoIntent, REQUEST_VIDEO_CAPTURE)
//            }
//        }
    }
//        if (cameraIntent.resolveActivity(packageManager) != null) {
//            startActivityForResult(videoIntent, REQUEST_VIDEO_CAPTURE)
//        }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //called when user presses ALLOW or DENY from Permission Request Popup
        when(requestCode){
            PERMISSION_CODE -> {
                if (grantResults.size > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED){
                    //permission from popup was granted
                    openCamera()
                } else{
                    //permission from popup was denied
                    Toast.makeText(this,"permission denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == RESULT_OK) {
//
//            //File object of camera image
//            val file = File(path, vFilename)
//
//            Toast.makeText(this,file.toString(), Toast.LENGTH_LONG).show()
//
//            //Uri of camera image
//            val uri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", file)
//            videoInputFile = file
////            myImageView.setImageURI(uri)
//            val frameExtractor = FrameExtractor(this)
//            executorService.execute {
//                try {
//
//                    frameExtractor.extractFrames(file.absolutePath)
//                } catch (exception: Exception) {
//                    exception.printStackTrace()
//                    this.runOnUiThread {
//                        Toast.makeText(this, "Failed to extract frames", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//            updateRecyclerView()
//        }
//    }
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode != RESULT_OK){
        return
    }
    when(requestCode){
        VIDEO_CAPTURE_CODE -> {
            val videoUri = data?.data
            if(videoUri == null){
                return
            }

            //you can extract frame using Glide also but here i did not //use

            val mimeType: String = contentResolver.getType(videoUri).toString()            //Save file to upload on server
            var savedfile = saveVideoToAppScopeStorage(this, videoUri, mimeType)
            if (savedfile != null) {
                videoInputFile = savedfile
            }
            val frameExtractor = FrameExtractor(this)
            executorService.execute {
                try {

                    savedfile?.let { frameExtractor.extractFrames(it.absolutePath) }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    this.runOnUiThread {
                        Toast.makeText(this, "Failed to extract frames", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            updateRecyclerView()
        }
    }

}



    fun saveVideoToAppScopeStorage(context: Context, videoUri: Uri?, mimeType: String?): File? {
        if(videoUri == null || mimeType == null){
            return null
        }

        val inputStream = context.contentResolver.openInputStream(videoUri)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), vFilename)
        file.deleteOnExit()
        file.createNewFile()
        val out = FileOutputStream(file)
        val bos = BufferedOutputStream(out)
        val buf = ByteArray(1024)
        inputStream?.read(buf)
        do {
            bos.write(buf)
        } while (inputStream?.read(buf) !== -1)
        //out.close()
        bos.close()
        inputStream.close()
        return file
    }
}









