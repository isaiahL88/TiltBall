package ca.yorku.eecs.mack.demotiltball87509;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

public class Results extends Activity {
    int laps, wallHits, lapTimeNum, pathTimeNum;
    TextView lapsLab, wallHitsLab, lapTime, pathTime;
    Button setupButton, exitButton;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.results);
        Intent i = getIntent();
        Bundle b = i.getExtras();
        laps = b.getInt("laps");
        wallHits = b.getInt("wallHits");
        lapTimeNum = b.getInt("lapTime");
        pathTimeNum = b.getInt("pathTime");

        lapTime = (TextView) findViewById(R.id.lapTime);
        pathTime = (TextView) findViewById(R.id.pathTime);
        lapsLab = (TextView) findViewById(R.id.laps);
        wallHitsLab = (TextView) findViewById(R.id.wallHits);
        lapsLab.setText("Laps = " + laps);
        wallHitsLab.setText("Wall Hits = " + wallHits);
        TextView totalTime = findViewById(R.id.totalTime);
        totalTime.setText("Total laps time = " + lapTimeNum);

        setupButton = (Button) findViewById(R.id.setupButton);
        exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
                System.exit(0);
            }
        });

        //calculate time
        long millis = lapTimeNum / laps;
        int seconds = (int) (millis / 60);
        int minutes = seconds / 60;
        String timeCounts = String.format("%d:%02d:%02d", minutes, seconds, millis);
        lapTime.setText("Lap time = " + timeCounts + " s (mean / lap)");

        //path time
        double percent = ((pathTimeNum + 1.0) / (lapTimeNum + 1.0) )* 100;
        pathTime.setText("In-path percentage = " + percent);
    }

    public void setup(View v){
        Intent i = new Intent(this, DemoTiltBallSetup.class);
        startActivity(i);
    }
}
