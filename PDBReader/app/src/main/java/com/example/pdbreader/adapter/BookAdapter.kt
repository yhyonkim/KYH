package com.example.pdbreader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pdbreader.R
import com.example.pdbreader.databinding.ItemBookBinding
import com.example.pdbreader.model.PdbBook

class BookAdapter(
    private val onBookClick: (PdbBook) -> Unit,
    private val onBookDelete: (PdbBook) -> Unit
) : ListAdapter<PdbBook, BookAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookViewHolder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: PdbBook) {
            binding.tvTitle.text = book.title
            binding.tvFormat.text = "${book.format.displayName} · ${book.fileSizeDisplay}"

            if (book.readingProgress > 0f) {
                binding.tvProgress.text = "${book.progressPercent}% 읽음"
                binding.progressBar.progress = book.progressPercent
            } else {
                binding.tvProgress.text = "읽지 않음"
                binding.progressBar.progress = 0
            }

            binding.root.setOnClickListener { onBookClick(book) }

            binding.btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_book_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_delete -> {
                            onBookDelete(book)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    private class BookDiffCallback : DiffUtil.ItemCallback<PdbBook>() {
        override fun areItemsTheSame(oldItem: PdbBook, newItem: PdbBook): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PdbBook, newItem: PdbBook): Boolean =
            oldItem == newItem
    }
}
