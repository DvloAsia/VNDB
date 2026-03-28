// MainActivity.java

package com.example.vndb;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.vndb.network.NetworkUtil;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!NetworkUtil.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            finish(); // Close the app or perform an alternative action
            return;
        }

        // Additional setup, listeners, etc.
        setupViews();
    }

    private void setupViews() {
        // Setup your views and animations here
        View myView = findViewById(R.id.my_view);
        // Start animation here, if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release resources, unbind services, etc.
    }
}

// NetworkUtil.java

package com.example.vndb.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtil {

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}