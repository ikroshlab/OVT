/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2013 Hannes Janetzek
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ikroshlab.vtmexample.test;


import com.ikroshlab.vtmexample.R;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.util.Log;
import android.widget.Toast;


/**
 * A simple start screen for the sample activities.
 */
public class Samples extends AppCompatActivity {

    final private int  REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_samples);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // START PERMISSION CHECK
        getPermissions();
	}


    private void activate() {

        logDebug("Samples - activate()");

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.samples);
        linearLayout.addView(createButton(BitmapTileMapActivity.class, "Raster maps"));
        linearLayout.addView(createButton(ThemeStylerActivity.class, "VTM styled online maps"));
        linearLayout.addView(createButton(MapsforgeMapActivity.class, "MapsForge offline maps"));
        linearLayout.addView(createButton(ForgeOnlineMapActivity.class, "MapsForge online maps"));
    }


	private Button createButton(final Class<?> clazz) {
		return this.createButton(clazz, null);
	}

	private Button createButton(final Class<?> clazz, String text) {
		Button button = new Button(this);
		if (text == null) {
			button.setText(clazz.getSimpleName());
		} else {
			button.setText(text);
			button.setBackgroundColor(Color.CYAN);
		}
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				startActivity(new Intent(Samples.this, clazz));
			}
		});
		return button;
	}





    ///////////////////////////////// handling runtime permissions  ////////////////////////////////
    private void getPermissions() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            //logDebug("permission has NOT been granted yet.");
            requestWritePermission();
        }
        else {
            //logDebug("permission has already been granted");
            try {
                activate();
            } catch (RuntimeException e)  {  ; }

        }

    }  // end getPermissions()


    private void requestWritePermission() {

        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            //logDebug("We need to show an explanation to be granted with the permission");
            // Show an explanation to the user *asynchronously* -- don't block this thread waiting for the user's response!
            // After the user sees the explanation, try again to request the permission.
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setMessage("Write_permission_rationale");
            bld.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int i) {
                    ActivityCompat.requestPermissions(Samples.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                }
            });
            bld.setCancelable(false);
            bld.create().show();

        }
        else { // No explanation needed, we can request the permission.
            //logDebug("No explanation needed, we can request the permission.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);

            // The callback method gets the result of the request...
        }

    }  // end requestLocationPermission()


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the permission-related task you need to do.
                    logDebug("Permission was granted by the user.");
                    try {
                        activate();
                    } catch (RuntimeException e)  {  ;  }
                }
                else {
                    // permission denied, boo! Disable the functionality that depends on this permission.
                    logDebug("Permission was denied by the user.");
                    Toast.makeText(Samples.this, "Cannot start - no permission", Toast.LENGTH_LONG).show();

                    this.finish();
                }
            }

            // other 'case' lines to check for other permissions this app might request

            default:  {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }

    }  // end onRequestPermissionsResult()
    ////////////////////////////////////////////////////////////////////////////////////////////////


    // handling Logging:
    private void logDebug(String msg) { Log.d("ikroshlab", " ### " + getClass().getName() + " ###  " + msg); }
}
