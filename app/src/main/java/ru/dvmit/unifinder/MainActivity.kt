package ru.dvmit.unifinder

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ru.dvmit.unifinder.R

class MainActivity : AppCompatActivity() {

    private lateinit var photo: ImageView
    private lateinit var ip:TextView
    private lateinit var server: Server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main)

        ip = findViewById(R.id.ip)
        photo = findViewById(R.id.photo)
        server = Server(1234, photo)

        photo.setOnLongClickListener{
            showRadioButtonDialog(this, (it as ImageView))

            return@setOnLongClickListener true
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)

        ip.text = ipAddress


    }



    fun showRadioButtonDialog(context: Context, imageView: ImageView){
        val options = arrayOf(Pair("CENTER",ImageView.ScaleType.CENTER) , Pair("MATRIX",ImageView.ScaleType.MATRIX), Pair("FIT_XY",ImageView.ScaleType.FIT_XY), Pair("FIT_START",ImageView.ScaleType.FIT_START), Pair("FIT_CENTER",ImageView.ScaleType.FIT_CENTER), Pair("FIT_END",ImageView.ScaleType.FIT_END), Pair("CENTER_CROP",ImageView.ScaleType.CENTER_CROP), Pair("CENTER_INSIDE",ImageView.ScaleType.CENTER_INSIDE) )
        var selectedOption = 0
        var optionsString = options.map { it.first }.toTypedArray()
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select a ScaleType")
        builder.setSingleChoiceItems(optionsString, selectedOption) { dialog, which ->
            selectedOption = which
        }
        builder.setPositiveButton("OK") { dialog, which ->




        }
        builder.setNegativeButton("Cancel") { dialog, which ->
            selectedOption = -1
        }
        val dialog = builder.create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            imageView.scaleType = if (selectedOption == -1) {
                ImageView.ScaleType.MATRIX
            } else {
                options[selectedOption].second
            }
            dialog.dismiss()
        }




    }

}