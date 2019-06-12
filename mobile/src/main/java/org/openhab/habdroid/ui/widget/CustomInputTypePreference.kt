package org.openhab.habdroid.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat

class CustomInputTypePreference constructor(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs) {
    private val inputType: Int

    init {
        context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.inputType)).apply {
            inputType = getInt(0, 0)
            recycle()
        }
    }

    fun createDialog(): DialogFragment {
        return PrefFragment.newInstance(key, inputType)
    }

    class PrefFragment : EditTextPreferenceDialogFragmentCompat() {
        override fun onBindDialogView(view: View?) {
            arguments?.getInt(KEY_INPUT_TYPE)?.let { type ->
                val editor = view?.findViewById<EditText>(android.R.id.edit)
                editor?.inputType = type
            }
            super.onBindDialogView(view)
        }

        companion object {
            private const val KEY_INPUT_TYPE = "inputType"

            fun newInstance(key: String, inputType: Int): PrefFragment {
                val f = PrefFragment()
                f.arguments = bundleOf(ARG_KEY to key, KEY_INPUT_TYPE to inputType)
                return f
            }
        }
    }
}