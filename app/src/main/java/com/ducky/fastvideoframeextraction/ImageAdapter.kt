package com.ducky.fastvideoframeextraction

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions


/**
 * Created by Duc Ky Ngo on 9/17/2021.
 * duckyngo1705@gmail.com
 */
//class ImageAdapter(
//    private val context: Context,
//    private val imagePaths: ArrayList<Uri>,
//    private val imageTitles: ArrayList<String>
//) : RecyclerView.Adapter<ImageAdapter.ImageHolder>() {
//
//    private var imageSize = 0
//    private fun setColumnNumber(context: Context, columnNum: Int) {
//        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        val metrics = DisplayMetrics()
//        wm.defaultDisplay.getMetrics(metrics)
//        val widthPixels = metrics.widthPixels
//        imageSize = widthPixels / columnNum
//    }
//
//
//
//    init {
//        setColumnNumber(context, 3)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
//        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.recycleview_img_item_layout, parent, false)
//        return ImageHolder(itemView)
//    }
//
//    override fun onBindViewHolder(holder: ImageHolder, position: Int) {
//        holder.title.text = imageTitles[position]
//
//        Glide.with(context)
//            .load(imagePaths[position])
//            .apply(
//                RequestOptions.centerCropTransform()
//                    .dontAnimate()
//                    .override(imageSize, imageSize)
//                    .placeholder(R.drawable.placeholder)
//            )
//            .thumbnail(0.5f)
//            .into(holder.imageView)
//    }
//
//    override fun getItemCount(): Int {
//        return imagePaths.size
//    }
//
//    class ImageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        var imageView: AppCompatImageView = itemView.findViewById(R.id.imageIv)
//        var title: TextView = itemView.findViewById(R.id.titleTv)
//    }
//}


class ImageAdapter(
    private val context: Context,
    private val imagePaths: ArrayList<Uri>,
    private val imageTitles: ArrayList<String>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<ImageAdapter.ImageHolder>() {

    private var imageSize = 0
    val selectedItems = mutableSetOf<Int>()

    init {
        setColumnNumber(context, 3)
    }

    interface OnItemClickListener {
        fun onItemClick(selectedIds: Set<Int>)
    }

    private fun setColumnNumber(context: Context, columnNum: Int) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        val widthPixels = metrics.widthPixels
        imageSize = widthPixels / columnNum
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.recycleview_img_item_layout, parent, false)
        return ImageHolder(itemView, imageSize)
    }


    override fun onBindViewHolder(holder: ImageHolder, position: Int) {
        holder.bind(imagePaths[position], imageTitles[position], selectedItems.contains(position))

        holder.itemView.setOnClickListener {
            if (selectedItems.contains(position)) {
                selectedItems.remove(position)
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            } else {
                selectedItems.add(position)
                holder.itemView.setBackgroundColor(Color.LTGRAY)
            }
            listener.onItemClick(selectedItems)
        }
    }
    override fun getItemCount(): Int {
        return imagePaths.size
    }

    class ImageHolder(itemView: View, private val imageSize: Int) : RecyclerView.ViewHolder(itemView) {
        var imageView: AppCompatImageView = itemView.findViewById(R.id.imageIv)
        var title: TextView = itemView.findViewById(R.id.titleTv)

        fun bind(imagePath: Uri, title: String, isSelected: Boolean) {
            Glide.with(itemView.context)
                .load(imagePath)
                .apply(
                    RequestOptions.centerCropTransform()
                        .dontAnimate()
                        .override(imageSize, imageSize)
                        .placeholder(R.drawable.placeholder)
                )
                .thumbnail(0.5f)
                .into(imageView)

            this.title.text = title

            itemView.isSelected = isSelected
            itemView.setBackgroundColor(if (isSelected) Color.LTGRAY else Color.TRANSPARENT)
        }
    }
}