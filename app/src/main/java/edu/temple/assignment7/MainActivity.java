//This app does a search based on the users input for books
//then it returns the list of books that matches by author or
//title and the user can then click on it to see the cover
package edu.temple.assignment7;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import edu.temple.audiobookplayer.AudiobookService;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookListFragmentInterface, ControlFragment.ControlFragmentInterface  {
    //Makes keys for the booklist and book selected that we will save
    private static final String KEY = "a";
    private static final String KEY2 = "b";
    private static final String KEY3 = "c";
    private static final String KEY4 = "d";
    private static final String KEY5 = "e";

    //Initializes all the values we will use
    String bookKey = "book";
    static BookList myList;
    boolean container2present;
    BookDetailsFragment bookDetailsFragment;
    ControlFragment audioControlFragment;
    static int place = -1;
    static int bookLength;
    static int spot;
    static String name;
    static int max;
    Button searchMain;
    RequestQueue requestQueue;
    static boolean canPlay = false;
    AudiobookService.MediaControlBinder myService;
    boolean isConnected;
    SharedPreferences preferences;
    boolean isPlaying;

    //Makes handler for the audio book progress
    Handler myHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {

            ControlFragment fragment = (ControlFragment) getSupportFragmentManager().
                    findFragmentById(R.id.audioControlContainer);
            canPlay =false;
            if(myService.isPlaying()&&((AudiobookService.BookProgress)msg.obj).getProgress()>=0&& max>=((AudiobookService.BookProgress)msg.obj).getProgress()){
                canPlay =false;
                if(fragment != null) {
                    fragment.seekBar.setProgress(((AudiobookService.BookProgress) msg.obj).getProgress());
                    spot = fragment.seekBar.getProgress();
                    isPlaying = myService.isPlaying();
                    SharedPreferences.Editor editor = preferences.edit();
                    //Saves postition of each book to its own preference
                    if (place != -1) {
                        editor.putInt(myList.get(place).getTitle()+"Position",spot);
                    }

                    //Keeps progress of current book
                    editor.putInt("spot",spot);
                    editor.apply();

                }

            }

            canPlay = true;

            return true;
        }
    });

    //Makes uses the media contol service
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myService = (AudiobookService.MediaControlBinder) service;
            myService.setProgressHandler(myHandler);
            isConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isConnected = false;
        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getPreferences(MODE_PRIVATE);

        //This is the search button
        //When clicked it launches the bookSearchActivty
        //the activity returns the user search
        //Go to activity result to see the outcome of the search
        searchMain = findViewById(R.id.searchMain);
        Intent serviceIntent = new Intent(MainActivity.this, AudiobookService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
        //Sets up audio fragment
        audioControlFragment = new ControlFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.audioControlContainer, audioControlFragment)
                .commit();






        searchMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent launchIntent = new Intent(MainActivity.this, BookSearchActivity.class);
                startActivityForResult(launchIntent, 1);



            }
        });
        container2present = findViewById(R.id.containerLandscape) != null;



        //This just makes the book details fragment for landscape mode
        if (container2present) {
            bookDetailsFragment = new BookDetailsFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.containerLandscape, bookDetailsFragment)
                    .commit();
        }


    }
    //Method to play the book when play is clicked
    @Override
    public void playClicked(){

        if(isConnected&& place!=-1){
            //Sets all the audio control stuff
            bookLength = myList.get(place).getDuration();
            canPlay = false;
            ControlFragment fragment = (ControlFragment) getSupportFragmentManager().
                    findFragmentById(R.id.audioControlContainer);
            fragment.seekBar.setProgress(0);
            spot = 0;
            fragment.seekBar.setMax(myList.get(place).getDuration());
            max = myList.get(place).getDuration();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("max",max);
            editor.apply();
            //Stops playing last book
            if(myService.isPlaying()) {
                myService.stop();
            }
            canPlay = true;
            //gets or makes a new file for the book
            File file = new File(getFilesDir(),myList.get(place).getTitle());
            //if the file already existed then it will play from file
            if(file.exists()) {
                if(preferences.getInt(myList.get(place).getTitle()+"Position",0)>10) {
                    myService.play(file, preferences.getInt(myList.get(place).getTitle()+"Position",0)-10);
                } else{
                    myService.play(file,0);
                }
                //if the file was empty it will stream it
            } else {
                myService.play(myList.get(place).getId());
                //This downloads the file for later
                String url = "https://kamorris.com/lab/audlib/download.php?id="+myList.get(place).getId();
                Thread thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try  {
                            downloadFile(url,file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                thread.start();

            }

            fragment.textView.setText("Now Playing: " + myList.get(place).getTitle());
            name = "Now Playing: " + myList.get(place).getTitle();
            preferences = getPreferences(MODE_PRIVATE);



            editor.putBoolean("isPlaying",true);
            editor.putInt("place",place);
            editor.apply();


        }

    }
    //Method to pause the book when pause is clicked
    @Override
    public void pauseClicked() {
        if(isConnected && place!=-1){
            myService.pause();
            //Keeps track of posistion when paused while shut down
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("isPlaying",false);
            editor.apply();

        }
    }
    //Method to stop the book when stop is clicked
    @Override
    public void stopClicked() {
        if(isConnected && place!=-1){
            myService.stop();
            ControlFragment fragment = (ControlFragment) getSupportFragmentManager().
                    findFragmentById(R.id.audioControlContainer);
            fragment.textView.setText("");
            name = "";
            spot = 0;
            canPlay = false;
            fragment.seekBar.setProgress(0);
            canPlay = true;
            //Sets the saved progress of the book that was playing to zero
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("isPlaying",false);

            editor.putInt(myList.get(place).getTitle()+"Position",0);
            editor.apply();

        }
    }
    //Allow the user to select when in the book they want to play
    @Override
    public void timeChanged(int progress) {
        if(isConnected && place!=-1&& canPlay) {
            myService.seekTo(progress);

        }
    }


    //This is the selection of the book from the list
    //When clicked it will display the book selected
    @Override
    public void itemClicked(int position, BookList myList) {
        //This sets the place if the app is restarted
        place = position;


        //This checks weather to put the display fragment in
        //container one if in portrait or container 2 in landscape
        if (!container2present) {

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, BookDetailsFragment.newInstance(myList.get(position)))
                    .addToBackStack(null)
                    .commit();



        } else {
            bookDetailsFragment.changeBook(myList.get(position));
        }


    }
    //Saves the book list and placement when rotated
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(KEY,place);
        outState.putParcelable(KEY2,myList);
        //Made save instances for the name of the book, duration in the book, and length
        outState.putString(KEY3,name);
        outState.putInt(KEY4,spot);
        outState.putInt(KEY5,max);

        super.onSaveInstanceState(outState);

    }


    //This sets the fragments when the app is rotated
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {

        //All these ifs are to check which fragment to put it into
        if (savedInstanceState != null) {
            place = savedInstanceState.getInt(KEY);
            myList = savedInstanceState.getParcelable(KEY2);
            name = savedInstanceState.getString(KEY3);
            spot = savedInstanceState.getInt(KEY4);
            max = savedInstanceState.getInt(KEY5);
            //sets the name, progress and length
            canPlay = false;
            ControlFragment fragment = (ControlFragment) getSupportFragmentManager().
                    findFragmentById(R.id.audioControlContainer);
            fragment.seekBar.setMax(max);
            fragment.seekBar.setProgress(spot);
            canPlay = true;
            if(place!=-1) {
                fragment.textView.setText("Now Playing: " + myList.get(place).getTitle());
            }
            if (!container2present&&myList != null) {

                if(place>=0) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, BookDetailsFragment.newInstance(myList.get(place)))
                            .addToBackStack(null)
                            .commit();
                } else {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, BookListFragment.newInstance(myList))
                            .addToBackStack(null)
                            .commit();
                }


            }else {
                if (myList != null && myList.size() != 0 && place !=-1) {
                    bookDetailsFragment.changeBook(myList.get(place));
                }
            }
                if (container2present&&myList != null && place !=-1) {

                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, BookListFragment.newInstance(myList))
                            .commit();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.containerLandscape, bookDetailsFragment)
                            .commit();
                    if(place>=0) {
                        bookDetailsFragment.changeBook(myList.get(place));
                    }

                }


            }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        requestQueue = Volley.newRequestQueue(this);
        //Makes sure result is good
        if(requestCode==1){
            if(resultCode==RESULT_OK){
                //Initializes the url, result and json
                String url = "https://kamorris.com/lab/cis3515/search.php?term=";
                String result = data.getStringExtra("result");
                //combines the url and result into one search url
                preferences = getPreferences(MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("search",result);
                editor.apply();
                JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET,url+result,null,new Response.Listener<JSONArray>() {

                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            //Makes a brand new book list and sets place to -1 so
                            //it resets the book selection
                            myList = new BookList();
                            place = -1;
                            for(int i = 0 ; i<response.length(); i++) {
                                //goes through each book one by one and adds it to the list

                                JSONObject book = response.getJSONObject(i);
                                String title = book.getString("title");
                                String author = book.getString("author");
                                int id = book.getInt("id");
                                int duration = book.getInt("duration");
                                String coverURL = book.getString("cover_url");

                                Book newBook = new Book(title,author,id,coverURL,duration);
                                myList.add(newBook);

                            }
                            //checks if container 2 is open
                            container2present = findViewById(R.id.containerLandscape) != null;
                            //makes the a brand new book list from the search
                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.container, BookListFragment.newInstance(myList))
                                    .commit();
                            //if container 2 is present makes a new one of them
                            if(container2present) {
                                bookDetailsFragment.makeEmpty();

                            }





                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }


                });
                requestQueue.add(jsonArrayRequest);




            }
        }
    }

    //This method handles the downloading of the file
    private static void downloadFile(String url, File outputFile) {
        try {
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            int contentLength = conn.getContentLength();

            DataInputStream stream = new DataInputStream(u.openStream());

            byte[] buffer = new byte[contentLength];
            stream.readFully(buffer);
            stream.close();

            DataOutputStream fos = new DataOutputStream(new FileOutputStream(outputFile));
            fos.write(buffer);
            fos.flush();
            fos.close();
        } catch(FileNotFoundException e) {
            return; // swallow a 404
        } catch (IOException e) {
            return; // swallow a 404
        }
    }
    //This handles all the saved stuff like:
    //The book list
    //The position of the book when closed
    //The legnth of the book
    //Also if the book is playing it will continue playing
    @Override
    protected void onStart() {
        super.onStart();
        //Kinda handle now playing
        place = preferences.getInt("place", -1);

        max = preferences.getInt("max",100);
        isPlaying =preferences.getBoolean("isPlaying",false);



        //This remakes the booklist
        requestQueue = Volley.newRequestQueue(this);
        String url = "https://kamorris.com/lab/cis3515/search.php?term=";
        String result = preferences.getString("search", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz");
        //combines the url and result into one search url

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET,url+result,null,new Response.Listener<JSONArray>() {

            @Override
            public void onResponse(JSONArray response) {
                try {
                    //Makes a brand new book list and sets place to -1 so
                    //it resets the book selection
                    myList = new BookList();


                    for(int i = 0 ; i<response.length(); i++) {
                        //goes through each book one by one and adds it to the list

                        JSONObject book = response.getJSONObject(i);
                        String title = book.getString("title");
                        String author = book.getString("author");
                        int id = book.getInt("id");
                        int duration = book.getInt("duration");
                        String coverURL = book.getString("cover_url");

                        Book newBook = new Book(title,author,id,coverURL,duration);
                        myList.add(newBook);

                        }
                    canPlay = false;
                    ControlFragment fragment = (ControlFragment) getSupportFragmentManager().
                            findFragmentById(R.id.audioControlContainer);
                    fragment.seekBar.setMax(max);
                    fragment.seekBar.setProgress(spot);
                    canPlay = true;
                    spot = preferences.getInt(myList.get(place).getTitle()+"Position",0);
                    if(place!=-1) {
                        fragment.textView.setText("Now Playing: " + myList.get(place).getTitle());
                    }
                    if (!container2present&&myList != null) {

                        if(place>=0) {
                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.container, BookListFragment.newInstance(myList))
                                    .addToBackStack(null)
                                    .commit();
                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.container, BookDetailsFragment.newInstance(myList.get(place)))
                                    .addToBackStack(null)
                                    .commit();
                        } else {
                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.container, BookListFragment.newInstance(myList))
                                    .addToBackStack(null)
                                    .commit();
                        }


                    }else {
                        if (myList != null && myList.size() != 0 && place !=-1) {
                            bookDetailsFragment.changeBook(myList.get(place));
                        }
                    }
                    if (container2present&&myList != null && place !=-1) {

                        getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.container, BookListFragment.newInstance(myList))
                                .commit();
                        getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.containerLandscape, bookDetailsFragment)
                                .commit();
                        if(place>=0) {
                            bookDetailsFragment.changeBook(myList.get(place));
                        }

                    }
                    //plays it if it was playing before
                    if(place!=-1) {
                        File file = new File(getFilesDir(), myList.get(place).getTitle());
                        if (isPlaying && file.exists()) {
                            myService.play(file, preferences.getInt(myList.get(place).getTitle()+"Position",0));
                        }
                    }






                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener(){
            @Override
            public void onErrorResponse(VolleyError error) {

            }


        });
        requestQueue.add(jsonArrayRequest);














    }

    }

