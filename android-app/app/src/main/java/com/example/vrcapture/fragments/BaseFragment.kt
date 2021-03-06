package com.example.vrcapture.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.example.vrcapture.R
import com.google.android.material.snackbar.Snackbar

abstract class BaseFragment<B : ViewBinding>(private val fragmentLayout: Int) : Fragment() {
    abstract val binding: B

    protected val outputDirectory: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DCIM}/VRCapture/"
        } else {
            "${requireContext().getExternalFilesDir(Environment.DIRECTORY_DCIM)?.path}/VRCapture/"
        }
    }

    private val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            onContinue()
        } else {
            view?.let { v ->
                Snackbar.make(v, R.string.message_no_permissions, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.label_ok) { ActivityCompat.finishAffinity(requireActivity()) }
                    .show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBackPressed()
                }
            })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (allPermissionsGranted()) {
            onContinue()
        } else {
            permissionRequest.launch(permissions.toTypedArray())
        }
    }

    protected fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun onContinue() {
        view?.let { v ->
            val wm = requireContext().applicationContext.getSystemService(AppCompatActivity.WIFI_SERVICE) as WifiManager
            val ip: String = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            Snackbar.make(v, ip, Snackbar.LENGTH_INDEFINITE).show()
            onPermissionGranted()
        }
    }

    open fun onPermissionGranted() = Unit

    abstract fun onBackPressed()
}
