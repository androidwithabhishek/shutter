package gupta.abhishek.shuttertimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val viewModel: ShutterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShutterScreen(viewModel) }
    }

    // Re-check whether the accessibility service is on after returning from system settings.
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}