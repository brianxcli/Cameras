package io.agora.rtc.videofukotlin.ui.activities
import android.content.Intent
import android.os.Bundle
import android.view.View
import io.agora.rtc.videofukotlin.R
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : BaseActivity(), View.OnClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initUI()
    }

    private fun initUI() {
        btn_join_channel.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            btn_join_channel.id -> {
                val intent = Intent(this, LiveRoomActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onAllPermissionsGranted() {

    }
}