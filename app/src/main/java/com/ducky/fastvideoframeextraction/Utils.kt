package com.ducky.fastvideoframeextraction

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * Created by Duc Ky Ngo on 9/13/2021.
 * duckyngo1705@gmail.com
 */
object Utils {


    /**
     * Get bitmap from ByteBuffer
     */
    fun fromBufferToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        result.copyPixelsFromBuffer(buffer)
//        val transformMatrix = Matrix()
//        val outputBitmap = Bitmap.createBitmap(result, 0, 0, result.width, result.height, transformMatrix, false)
//        outputBitmap.density = DisplayMetrics.DENSITY_DEFAULT
//        return outputBitmap
        return result
    }


    fun saveImageToFile(bmp: Bitmap?, file: File, isPNG: Boolean=false, quality: Int=90): File? {
        if (bmp == null) {
            return null
        }
        try {
            val fos = FileOutputStream(file)
            if (isPNG) {
                bmp.compress(Bitmap.CompressFormat.PNG, quality, fos)
            } else {
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            fos.flush()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }


    fun deleteFile(f: File) {
        if (f.isDirectory) {
            val files = f.listFiles()
            if (files != null && files.size > 0) {
                for (i in files.indices) {
                    deleteFile(files[i])
                }
            }
        }
        f.delete()
    }


    fun saveMediaToStorage(context: Context?, bitmap: Bitmap, filename: String) {
        //Generating a file name

        //Output stream
        var fos: OutputStream? = null

        //For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            context?.contentResolver?.also { resolver ->

                //Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                //Inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            //These for devices running on android < Q
            //So I don't think an explanation is needed here
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            //Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }



    fun angleBetweenVectors(p1: PointF, p2: PointF, q1: PointF, q2: PointF): Float {
        val vector1 = PointF(p2.x - p1.x, p2.y - p1.y)
        val vector2 = PointF(q2.x - q1.x, q2.y - q1.y)

        val dotProduct = vector1.x * vector2.x + vector1.y * vector2.y
        val magnitude1 = sqrt(vector1.x * vector1.x + vector1.y * vector1.y)
        val magnitude2 = sqrt(vector2.x * vector2.x + vector2.y * vector2.y)

        val cosTheta = dotProduct / (magnitude1 * magnitude2)
        val angle = acos(cosTheta)

        // Determina il segno dell'angolo
        val crossProduct = vector1.x * vector2.y - vector1.y * vector2.x
        return if (crossProduct < 0) -angle else angle
    }

    // Ruota un punto attorno all'origine
    fun rotatePoint(p: PointF, angle: Float): PointF {
        val cosTheta = cos(angle)
        val sinTheta = sin(angle)
        return PointF(
            cosTheta * p.x - sinTheta * p.y,
            sinTheta * p.x + cosTheta * p.y
        )
    }

    // Trasla un punto
    fun translatePoint(p: PointF, translation: PointF): PointF {
        return PointF(p.x + translation.x, p.y + translation.y)
    }

    // Funzione principale per applicare la rototraslazione
    fun transformVectors(p_a: PointF, p_b: PointF, q_c: PointF, q_d: PointF, r_d: PointF, r_e: PointF): Triple<PointF, PointF, PointF> {
        // Calcola l'angolo di rotazione necessario
        val angle = angleBetweenVectors(q_c, q_d, p_a, p_b)

        // Calcola la traslazione necessaria
        val translated_q_d = rotatePoint(q_d, angle)
        val translation = PointF(p_b.x - translated_q_d.x, p_b.y - translated_q_d.y)

        // Applica la rotazione e traslazione al secondo vettore
        val rotated_q_c = rotatePoint(q_c, angle)
        val translated_q_c = translatePoint(rotated_q_c, translation)
        val translated_q_d_final = translatePoint(rotatePoint(q_d, angle), translation)

        // Applica la stessa rotazione e traslazione al terzo vettore
        val rotated_r_d = rotatePoint(r_d, angle)
        val translated_r_d = translatePoint(rotated_r_d, translation)
        val rotated_r_e = rotatePoint(r_e, angle)
        val translated_r_e = translatePoint(rotated_r_e, translation)

        return Triple(translated_q_c, translated_q_d_final, translated_r_e)


    }

}