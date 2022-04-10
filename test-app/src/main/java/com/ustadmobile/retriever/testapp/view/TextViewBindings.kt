package com.ustadmobile.retriever.testapp.view

import android.text.format.DateFormat
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.ustadmobile.retriever.testapp.R
import java.util.*

@BindingAdapter("lastSeenTime")
fun TextView.setLastSeenTime(lastSeenTime: Long) {
    val formatter = DateFormat.getTimeFormat(context)
    text = context.getString(R.string.last_seen_time, formatter.format(Date(lastSeenTime)))
}
