package com.namaz.reminder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        val tv = TextView(this);
        tv.text = "🕌 Namaz Reminder Pro Ready";
        tv.textSize = 22f;
        setContentView(tv);
    }
}
