package com.ustadmobile.retriever.testapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.ustadmobile.retriever.testapp.R

class TextEditEntryDialogFragment: DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.enter_url)

        builder.setPositiveButton(R.string.ok, DialogInterface.OnClickListener { dialog, which ->  })

        return super.onCreateDialog(savedInstanceState)
    }
}