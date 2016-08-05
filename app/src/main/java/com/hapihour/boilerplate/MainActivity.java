package com.hapihour.boilerplate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    public static class MessageViewHolder extends  RecyclerView.ViewHolder {

        public TextView messageTextView;
        public TextView messengerTextView;
        public CircleImageView messengerImageView;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messengerTextView  = (TextView) itemView.findViewById(R.id.messengerTextView);
            messengerImageView = (CircleImageView) itemView.findViewById(R.id.messengerImageView);

        }

    }

    //consts
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 10;
    public static final String MESSAGES_CHILD = "messages";


    //Firebase Vars
    private FirebaseAuth currentFirebaseAuth;
    private FirebaseUser currentUser;
    private SharedPreferences userPrefs;
    private DatabaseReference currFirebaseDatabaseReference;
    private FirebaseRecyclerAdapter<FriendlyMessage/*this is the object Model*/, MessageViewHolder> currFirebaseAdapter;
    private FirebaseRemoteConfig currFirebaseRemoteConfig;
    private FirebaseAnalytics fbAnalytics;

    //uservars
    private String currUsername;
    private String currUserPhotoUrl;

    private GoogleApiClient currGoogleApiClient;
    //tag for loggging
    private static final String TAG = "MainActivity";
    private static final int REQUEST_INVITE = 1;


    //init Ui elements
    private Button sendButton;
    private RecyclerView messageRecyclerView;
    private LinearLayoutManager messageLinearLayoutManager;
    private ProgressBar messageProgress;
    private EditText messageEditText;
    private AdView currAdView;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initi prefs
        userPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        //use remote config items
        currFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        //analytics
        fbAnalytics = FirebaseAnalytics.getInstance(this);

        //set default username
        currUsername = ANONYMOUS;
        currGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this/*Fragment activity*/, this/*Connection failed listener*/)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .addApi(AppInvite.API)
                .build();

        //load ad
        currAdView = (AdView) findViewById(R.id.adView);
        AdRequest currRequest = new AdRequest.Builder().build();
        currAdView.loadAd(currRequest);

        //initialize Progressbar and RecyclerView
        messageProgress = (ProgressBar) findViewById(R.id.progressBar);
        messageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        //used to stack messages on the layout
        messageLinearLayoutManager = new LinearLayoutManager(this);
        messageLinearLayoutManager.setStackFromEnd(true);
        //add Items to the bottom
        messageRecyclerView.setLayoutManager(messageLinearLayoutManager);


        //REMOTE CONFIG
        //define Remote config settings
        FirebaseRemoteConfigSettings firebaseRemoteConfigSettings =
                new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(true)
                        .build();

        //define default config values .Defaults are used when fetched config values are not
        //available eg an error occured when fetchong from server
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put("friendly_msg_length", 10L);

        //appply config setting
        currFirebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings);
        currFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        //Fetch the remote settings
        fetchConfig();
        //END REMOTE CONFIG


        //hide progressbar
        //add new child entries
        //get a firebase reference
        currFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        //use the reference to populate a RecyclerView
        //initialize the adapter
        currFirebaseAdapter = new FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(
                FriendlyMessage.class,
                R.layout.item_message,
                MessageViewHolder.class,
                currFirebaseDatabaseReference.child(MESSAGES_CHILD)){
            // add items to the adapter
            @Override
            protected void populateViewHolder(MessageViewHolder viewHolder, FriendlyMessage model, int position) {
                messageProgress.setVisibility(ProgressBar.INVISIBLE);
                viewHolder.messageTextView.setText(model.getText());
                viewHolder.messengerTextView.setText(model.getName());
                if(model.getPhotoUrl() == null) {
                    viewHolder.messengerImageView
                            .setImageDrawable(ContextCompat
                            .getDrawable(MainActivity.this,
                                    R.drawable.ic_account_circle_black_36dp));
                } else {
                    Glide.with(MainActivity.this)
                            .load(model.getPhotoUrl())
                            .into(viewHolder.messengerImageView);
                }

            }
        };

        currFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = currFirebaseAdapter.getItemCount();
                int lastVisiblePosition = messageLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                //if the recyclerview is initially being loaded ot the
                //user is at the bottom of the list , scroll to the bottom
                //of the list to show newl;y added message
                if(lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount -1) &&
                        lastVisiblePosition == (positionStart -1))) {
                    messageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });

        //set the  layout manager for stacking items
        messageRecyclerView.setLayoutManager(messageLinearLayoutManager);
        //set the adapter to aid with data arranging
        messageRecyclerView.setAdapter(currFirebaseAdapter);


        messageEditText  = (EditText) findViewById(R.id.messageEditText);
        //set input limits on the text
        messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(userPrefs
                .getInt(CodelabPreferences.FRIENDLY_MSG_LENGTH, DEFAULT_MSG_LENGTH_LIMIT))});
        //add a text watcher to tell us when the textbox has data so we can activate post
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.toString().trim().length() > 0) {
                    sendButton.setEnabled(true);
                } else {
                    sendButton.setEnabled(false);
                }

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Send Messages on Click
                //Populate the object to we can push it to the database
                FriendlyMessage newMessage = new
                        FriendlyMessage(/*already validated by the text water*/messageEditText.getText().toString(),
                        currUsername,
                        currUserPhotoUrl);
                //use the database reference to push the object up
                currFirebaseDatabaseReference.child(MESSAGES_CHILD/*String name of the object we want to push*/)
                        .push().setValue(newMessage);
                //clear the message field
                messageEditText.setText("");
            }
        });


        //begin firebase Auth
        currentFirebaseAuth = FirebaseAuth.getInstance();
        currentUser = currentFirebaseAuth.getCurrentUser();

        //if user doesnt exist redirect to sign in
        if(currentUser == null ) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        } else {
            //if user exists
            currUsername = currentUser.getDisplayName();
            if(currentUser.getPhotoUrl() != null) {
                currUserPhotoUrl = currentUser.getPhotoUrl().toString();
            }
        }
    }

    //Fetch the config to determine the allowed length of messages
    private void fetchConfig() {
         long cacheExpiration = 3600;
        //be kind to the cloud wait an hour
        //if in dev mode the cache is 0
        //all fetches go to the server
        //only for dev mode
        if(currFirebaseRemoteConfig.getInfo().getConfigSettings()
                .isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        currFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        //Made the feched config available bia
                        //FirebaseRemoteConfig
                        currFirebaseRemoteConfig.activateFetched();
                        applyRetrievedLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        //an error occured whe n fetching the config
                        Log.w(TAG, "Error fecthing the config: "+
                        e.getMessage());
                        applyRetrievedLengthLimit();
                    }
                });

    }

    //Apply the remote configs that you have just fetched
    private void applyRetrievedLengthLimit(){
        Long friendly_msg_length =
                currFirebaseRemoteConfig.getLong("friendly_msg_length");
        messageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(friendly_msg_length.intValue())
        });
        Log.d(TAG, "FML is: " +friendly_msg_length);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sign_out_menu:
                currentFirebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(currGoogleApiClient);
                currUsername = ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                return true;
            case R.id.fresh_config_menu:
                fetchConfig();
                return true;
            case R.id.invite_menu:
                sendInvitation();
                return true;
            case R.id.crash_menu:
                FirebaseCrash.logcat(Log.ERROR, TAG, "crash caused");
                causeCrash();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void causeCrash() {
        throw new NullPointerException("Fake Null pointer Exception");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: "+connectionResult);
        Toast.makeText(this, "Google Play Services error. ", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onStart() {
        super.onStart();
        //check if user is signed in
    }

    @Override
    protected void onPause() {
        //pause resource intensive operations e.g map
        if(currAdView != null){
            currAdView.pause();
        }
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
        //yaaay user is back lets do cool stuff
        if(currAdView != null) {
            currAdView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if(currAdView != null) {
            currAdView.destroy();
        }
        super.onDestroy();
        //got mem usage .. dump that bitch BYE FELICIA
    }

    private void sendInvitation() {
        Intent intent = new AppInviteInvitation.IntentBuilder(getString(R.string.invitation_title))
                .setMessage(getString(R.string.invitation_message))
                .setCallToActionText(getString(R.string.invitation_cta))
                .build();
        startActivityForResult(intent, REQUEST_INVITE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode +
        ", resultCode=" + resultCode);

        if(requestCode == REQUEST_INVITE) {
            if(resultCode == RESULT_OK) {
                //Track this action using analytics
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "sent");
                fbAnalytics.logEvent(FirebaseAnalytics.Event.SHARE,
                        payload);
                //check how many invites were sent out
                String[] ids = AppInviteInvitation
                        .getInvitationIds(resultCode, data);
                Log.d(TAG, "Invites Sent: " + ids.length);
            } else {
                //Track this action using analytics
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "not sent");
                fbAnalytics.logEvent(FirebaseAnalytics.Event.SHARE,
                        payload);
                //Sending failed or ite was canceled
                Log.d(TAG, "Failed to invite");
            }
        }
    }
}
