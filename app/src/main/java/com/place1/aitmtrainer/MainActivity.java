package com.place1.aitmtrainer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 100;

    private LinearLayout setupPanel, callPanel;
    private EditText serverEdit, employeeEdit;
    private Spinner positionSpinner, customerTypeSpinner, difficultySpinner;
    private TextView statusView, callStateView, callMetaView, goalView, timerView, micHintView;
    private Button pauseButton;

    private JSONArray positions = new JSONArray();
    private JSONArray customerTypes = new JSONArray();

    private String currentPositionCode = "newbie";
    private Integer sessionId = null;

    private boolean paused = false;
    private boolean finishing = false;
    private boolean recording = false;
    private boolean aiPlaying = false;
    private boolean sending = false;

    private Handler handler = new Handler();
    private SharedPreferences prefs;
    private TextToSpeech localTts;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File currentAudioFile;

    private long callStartMillis = 0L;
    private long recordStartMillis = 0L;
    private long silentStartMillis = 0L;
    private Runnable timerRunnable;
    private Runnable amplitudeRunnable;
    private boolean speechDetected = false;
    private int voiceHitCount = 0;
    private int silenceHitCount = 0;

    private final int AMP_THRESHOLD = 900;
    private final int MIN_RECORD_MS = 2500;
    private final int SILENCE_STOP_MS = 2100;
    private final int MAX_RECORD_MS = 18000;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ai_tm_trainer", MODE_PRIVATE);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }

        localTts = new TextToSpeech(this, this);
        buildUi();
        loadInitialData();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            localTts.setLanguage(Locale.KOREAN);
            localTts.setSpeechRate(1.0f);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(246, 248, 252));

        TextView title = new TextView(this);
        title.setText("AI TM 콜드콜 트레이닝");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(15,23,42));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v3.2 안정 대화 모드 · 말이 끝난 뒤 자연스럽게 응답");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(100,116,139));
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        setupPanel.setBackground(cardBg(Color.WHITE));
        scroll.addView(setupPanel);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        setupPanel.addView(label("서버 주소"));
        serverEdit = input();
        serverEdit.setText(prefs.getString("server_url", "http://172.30.0.53:8031"));
        setupPanel.addView(serverEdit);

        Button loadButton = button("서버 연결 / 포지션 / 유형 불러오기", true);
        loadButton.setOnClickListener(v -> { saveServerUrl(); loadInitialData(); });
        setupPanel.addView(loadButton);

        setupPanel.addView(sectionTitle("훈련 설정"));

        setupPanel.addView(label("이름"));
        employeeEdit = input();
        employeeEdit.setHint("예: 김민수");
        employeeEdit.setText(prefs.getString("employee_name", ""));
        setupPanel.addView(employeeEdit);

        setupPanel.addView(label("포지션"));
        positionSpinner = spinner();
        setupPanel.addView(positionSpinner);

        setupPanel.addView(label("고객 유형"));
        customerTypeSpinner = spinner();
        setupPanel.addView(customerTypeSpinner);

        setupPanel.addView(label("난이도"));
        difficultySpinner = spinner();
        difficultySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"초급", "중급", "고급"}));
        setupPanel.addView(difficultySpinner);

        goalView = infoBox("서버 연결 버튼을 눌러 포지션과 고객유형을 불러오세요.");
        setupPanel.addView(goalView);

        Button startButton = button("시작하기", true);
        startButton.setTextSize(18);
        startButton.setOnClickListener(v -> startSession());
        setupPanel.addView(startButton);

        callPanel = new LinearLayout(this);
        callPanel.setOrientation(LinearLayout.VERTICAL);
        callPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        callPanel.setPadding(dp(18), dp(22), dp(18), dp(18));
        callPanel.setVisibility(View.GONE);
        root.addView(callPanel, new LinearLayout.LayoutParams(-1,0,1));

        callMetaView = centeredText(15, Color.rgb(71,85,105), false);
        callPanel.addView(callMetaView);

        timerView = centeredText(42, Color.rgb(37,99,235), true);
        timerView.setText("00:00");
        timerView.setPadding(0, dp(34), 0, dp(8));
        callPanel.addView(timerView);

        callStateView = centeredText(28, Color.rgb(15,23,42), true);
        callStateView.setText("대기 중");
        callPanel.addView(callStateView, new LinearLayout.LayoutParams(-1,-2));

        micHintView = centeredText(15, Color.rgb(100,116,139), false);
        micHintView.setText("통화 준비 중");
        micHintView.setPadding(0, dp(12), 0, dp(28));
        callPanel.addView(micHintView);

        TextView guide = infoBox("텍스트는 표시하지 않습니다.\nAI 고객이 말한 뒤 자동으로 녹음됩니다.\n상담원이 말을 마치고 약 2초 정도 조용하면 응답을 준비합니다.");
        guide.setGravity(Gravity.CENTER);
        callPanel.addView(guide);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(24), 0, 0);

        pauseButton = button("일시정지", false);
        pauseButton.setOnClickListener(v -> togglePause());
        row.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(52), 1));

        Space sp = new Space(this);
        row.addView(sp, new LinearLayout.LayoutParams(dp(10), 1));

        Button finishButton = button("종료/평가", true);
        finishButton.setOnClickListener(v -> finishSession());
        row.addView(finishButton, new LinearLayout.LayoutParams(0, dp(52), 1));

        callPanel.addView(row, new LinearLayout.LayoutParams(-1,-2));

        statusView = new TextView(this);
        statusView.setText("상태: 준비 중");
        statusView.setTextColor(Color.rgb(71,85,105));
        statusView.setPadding(0, dp(10), 0, 0);
        root.addView(statusView);

        setContentView(root);
    }

    private TextView centeredText(int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.rgb(15,23,42));
        v.setPadding(0, dp(22), 0, dp(8));
        return v;
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.rgb(52,64,84));
        v.setPadding(0, dp(12), 0, dp(4));
        return v;
    }

    private EditText input() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(inputBg());
        e.setMinHeight(dp(48));
        return e;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        s.setMinimumHeight(dp(48));
        s.setBackground(inputBg());
        return s;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(37,99,235));
        b.setBackground(primary ? buttonBg() : outlineButtonBg());
        b.setMinHeight(dp(50));
        return b;
    }

    private TextView infoBox(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(Color.rgb(71,85,105));
        v.setPadding(dp(14),dp(12),dp(14),dp(12));
        v.setBackground(infoBg());
        return v;
    }

    private GradientDrawable cardBg(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(18));g.setStroke(1,Color.rgb(226,232,240));return g;}
    private GradientDrawable inputBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(12));g.setStroke(1,Color.rgb(203,213,225));return g;}
    private GradientDrawable buttonBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(37,99,235),Color.rgb(124,58,237)});g.setCornerRadius(dp(14));return g;}
    private GradientDrawable outlineButtonBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(239,246,255));g.setCornerRadius(dp(14));g.setStroke(1,Color.rgb(147,197,253));return g;}
    private GradientDrawable infoBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(248,250,252));g.setCornerRadius(dp(14));g.setStroke(1,Color.rgb(226,232,240));return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    private void saveServerUrl(){prefs.edit().putString("server_url", server()).apply();}
    private String server(){return serverEdit.getText().toString().trim().replaceAll("/+$","");}
    private void setStatus(String text){runOnUiThread(() -> statusView.setText("상태: "+text));}
    private void setCallState(String text){runOnUiThread(() -> callStateView.setText(text));}
    private void setMicHint(String text){runOnUiThread(() -> micHintView.setText(text));}

    private JSONObject requestJson(String path, String method, JSONObject body) throws Exception {
        URL url = new URL(server()+path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);
        if(body!=null){
            conn.setDoOutput(true);
            OutputStream out=conn.getOutputStream();
            out.write(body.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
        }
        InputStream is=(conn.getResponseCode()>=200&&conn.getResponseCode()<300)?conn.getInputStream():conn.getErrorStream();
        String text=readAll(is);
        if(conn.getResponseCode()<200||conn.getResponseCode()>=300)throw new RuntimeException(text);
        return new JSONObject(text);
    }

    private JSONArray requestJsonArray(String path) throws Exception {
        URL url = new URL(server()+path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        return new JSONArray(readAll(conn.getInputStream()));
    }

    private String readAll(InputStream is) throws Exception {
        BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=br.readLine())!=null)sb.append(line);
        br.close();
        return sb.toString();
    }

    private void loadInitialData(){
        new Thread(() -> {
            try{
                saveServerUrl();
                requestJson("/api/health","GET",null);
                positions=requestJsonArray("/api/positions");
                ArrayList<String> names=new ArrayList<>();
                for(int i=0;i<positions.length();i++){
                    JSONObject p=positions.getJSONObject(i);
                    if(p.optInt("is_active",1)==1)names.add(p.getString("name"));
                }
                runOnUiThread(() -> {
                    positionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                    positionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id){
                            try{
                                currentPositionCode=findPositionCodeByName(String.valueOf(positionSpinner.getSelectedItem()));
                                loadCustomerTypes();
                                updateGoalText();
                            }catch(Exception ignored){}
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
                    });
                });
                setStatus("서버 연결 완료");
                loadCustomerTypes();
            }catch(Exception e){
                setStatus("서버 연결 실패: "+e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,"서버 주소와 관리자 서버 실행 상태를 확인하세요.",Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String findPositionCodeByName(String name)throws Exception{
        for(int i=0;i<positions.length();i++){
            JSONObject p=positions.getJSONObject(i);
            if(name.equals(p.getString("name")))return p.getString("code");
        }
        return "newbie";
    }

    private JSONObject currentPositionObject(){
        try{
            for(int i=0;i<positions.length();i++){
                JSONObject p=positions.getJSONObject(i);
                if(currentPositionCode.equals(p.getString("code")))return p;
            }
        }catch(Exception ignored){}
        return null;
    }

    private void updateGoalText(){
        try{
            JSONObject p=currentPositionObject();
            if(p==null)return;
            JSONArray goals=p.optJSONArray("goals");
            ArrayList<String> names=new ArrayList<>();
            if(goals!=null){
                for(int i=0;i<goals.length();i++){
                    JSONObject g=goals.getJSONObject(i);
                    if(g.optInt("is_active",1)==1&&g.optInt("is_core",1)==1)names.add(g.optString("title",""));
                }
            }
            runOnUiThread(() -> goalView.setText("목표: "+join(names," · ")));
        }catch(Exception ignored){}
    }

    private String join(ArrayList<String> arr,String sep){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<arr.size();i++){
            if(i>0)sb.append(sep);
            sb.append(arr.get(i));
        }
        return sb.toString();
    }

    private void loadCustomerTypes(){
        new Thread(() -> {
            try{
                customerTypes=requestJsonArray("/api/customer-types?position="+currentPositionCode);
                ArrayList<String> names=new ArrayList<>();
                for(int i=0;i<customerTypes.length();i++){
                    JSONObject item=customerTypes.getJSONObject(i);
                    if(item.optInt("is_active",1)==1)names.add(item.getString("name"));
                }
                runOnUiThread(() -> {
                    customerTypeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                    setStatus("포지션/유형 불러오기 완료");
                });
            }catch(Exception e){
                setStatus("유형 불러오기 실패: "+e.getMessage());
            }
        }).start();
    }

    private int selectedCustomerTypeId()throws Exception{
        String selectedName=String.valueOf(customerTypeSpinner.getSelectedItem());
        for(int i=0;i<customerTypes.length();i++){
            JSONObject item=customerTypes.getJSONObject(i);
            if(selectedName.equals(item.getString("name")))return item.getInt("id");
        }
        return 1;
    }

    private String selectedCustomerTypeName(){
        Object o=customerTypeSpinner.getSelectedItem();
        return o==null?"-":String.valueOf(o);
    }

    private String selectedPositionName(){
        Object o=positionSpinner.getSelectedItem();
        return o==null?"-":String.valueOf(o);
    }

    private void startSession(){
        String employee=employeeEdit.getText().toString().trim();
        if(employee.isEmpty()){
            Toast.makeText(this,"이름을 입력하세요.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(positionSpinner.getSelectedItem()==null||customerTypeSpinner.getSelectedItem()==null){
            Toast.makeText(this,"서버 연결 버튼으로 포지션/유형을 먼저 불러오세요.",Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("employee_name",employee).putString("server_url",server()).apply();
        paused=false;finishing=false;sessionId=null;
        callStartMillis=System.currentTimeMillis();

        runOnUiThread(() -> {
            setupPanel.setVisibility(View.GONE);
            callPanel.setVisibility(View.VISIBLE);
            callMetaView.setText(selectedPositionName()+" / "+selectedCustomerTypeName()+" / "+difficultySpinner.getSelectedItem());
            pauseButton.setText("일시정지");
            startTimer();
            setCallState("연결 중");
            setMicHint("AI 고객을 준비하고 있습니다");
        });

        new Thread(() -> {
            try{
                JSONObject body=new JSONObject();
                body.put("employee_name",employee);
                body.put("position_code",currentPositionCode);
                body.put("customer_type_id",selectedCustomerTypeId());
                body.put("difficulty",String.valueOf(difficultySpinner.getSelectedItem()));
                JSONObject res=requestJson("/api/voice/session/start","POST",body);
                sessionId=res.getInt("session_id");
                playAiResponse(res.optString("audio_url",""),res.optString("first_text","여보세요?"),res.optBoolean("use_local_tts",false));
            }catch(Exception e){
                setStatus("세션 시작 실패: "+e.getMessage());
                playLocalTts("서버 음성 세션을 시작하지 못했습니다. 관리자 서버와 API 키 설정을 확인해주세요.", false);
            }
        }).start();
    }

    private void startTimer(){
        if(timerRunnable!=null)handler.removeCallbacks(timerRunnable);
        timerRunnable=new Runnable(){
            @Override public void run(){
                long sec=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);
                timerView.setText(String.format(Locale.KOREA,"%02d:%02d",sec/60,sec%60));
                if(!finishing)handler.postDelayed(this,1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void togglePause(){
        paused=!paused;
        if(paused){
            stopRecording();
            stopPlayback();
            setCallState("일시정지");
            setStatus("일시정지됨");
            setMicHint("다시 시작을 누르면 이어집니다");
            pauseButton.setText("다시 시작");
        }else{
            pauseButton.setText("일시정지");
            setStatus("다시 시작");
            startRecordingAfterAi();
        }
    }

    private void playAiResponse(String audioUrl, String textFallback, boolean useLocalTts){
        if(audioUrl!=null && audioUrl.length()>0 && !useLocalTts){
            playServerAudio(audioUrl);
        }else{
            playLocalTts(textFallback, true);
        }
    }

    private void playServerAudio(String audioUrl){
        runOnUiThread(() -> {
            try{
                aiPlaying=true;
                stopRecording();
                stopPlayback();
                setCallState("AI 고객 말하는 중");
                setStatus("고객 응답 중");
                setMicHint("AI 고객이 말하고 있습니다");
                player=new MediaPlayer();
                player.setDataSource(this, Uri.parse(audioUrl));
                player.setOnPreparedListener(mp -> mp.start());
                player.setOnCompletionListener(mp -> {
                    aiPlaying=false;
                    stopPlayback();
                    startRecordingAfterAi();
                });
                player.setOnErrorListener((mp,what,extra) -> {
                    aiPlaying=false;
                    stopPlayback();
                    setStatus("음성 재생 실패");
                    startRecordingAfterAi();
                    return true;
                });
                player.prepareAsync();
            }catch(Exception e){
                aiPlaying=false;
                setStatus("음성 재생 오류: "+e.getMessage());
                startRecordingAfterAi();
            }
        });
    }

    private void playLocalTts(String text, boolean listenAfter){
        runOnUiThread(() -> {
            aiPlaying=true;
            stopRecording();
            setCallState("AI 고객 말하는 중");
            setStatus("고객 응답 중");
            setMicHint("AI 고객이 말하고 있습니다");
            localTts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"local_ai_"+System.currentTimeMillis());
            int delay=Math.max(1600,Math.min(9000,text.length()*130));
            handler.postDelayed(() -> {
                aiPlaying=false;
                if(listenAfter)startRecordingAfterAi();
            },delay);
        });
    }

    private void startRecordingAfterAi(){
        if(paused||finishing||aiPlaying||sending)return;
        handler.postDelayed(() -> startRecording(),350);
    }

    private void startRecording(){
        if(paused||finishing||aiPlaying||recording||sending)return;
        try{
            currentAudioFile=new File(getCacheDir(),"turn_"+System.currentTimeMillis()+".m4a");
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(currentAudioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording=true;
            recordStartMillis=System.currentTimeMillis();
            silentStartMillis=0;
            speechDetected=false;
            voiceHitCount=0;
            silenceHitCount=0;
            setCallState("상담원 말하는 중");
            setStatus("녹음 중");
            setMicHint("말이 끝나면 자동으로 전송합니다");
            monitorAmplitude();
        }catch(Exception e){
            recording=false;
            setStatus("녹음 시작 실패: "+e.getMessage());
            handler.postDelayed(() -> startRecording(),1500);
        }
    }

    private void monitorAmplitude(){
        if(amplitudeRunnable!=null)handler.removeCallbacks(amplitudeRunnable);
        amplitudeRunnable=new Runnable(){
            @Override public void run(){
                if(!recording||recorder==null)return;

                long elapsed=System.currentTimeMillis()-recordStartMillis;
                int amp=0;
                try{amp=recorder.getMaxAmplitude();}catch(Exception ignored){}

                // 실제 발화가 여러 번 감지되어야 speechDetected 처리
                if(amp>=AMP_THRESHOLD){
                    voiceHitCount++;
                    silenceHitCount=0;
                    if(voiceHitCount>=2){
                        speechDetected=true;
                        silentStartMillis=0;
                        setMicHint("말씀을 듣고 있습니다");
                    }
                }else{
                    if(speechDetected){
                        silenceHitCount++;
                    }
                }

                // 발화가 감지된 뒤에만 침묵 종료 판단
                if(speechDetected && elapsed>MIN_RECORD_MS){
                    if(amp<AMP_THRESHOLD){
                        if(silentStartMillis==0)silentStartMillis=System.currentTimeMillis();
                    }else{
                        silentStartMillis=0;
                    }

                    if(silentStartMillis>0 && System.currentTimeMillis()-silentStartMillis>SILENCE_STOP_MS){
                        stopRecordingAndSend();
                        return;
                    }
                }

                // 아무 말도 하지 않은 상태는 서버로 보내지 않고 계속 듣기
                if(!speechDetected && elapsed>6500){
                    stopRecording();
                    setCallState("듣는 중");
                    setStatus("대기 중");
                    setMicHint("말씀이 없으면 계속 기다립니다");
                    if(!paused && !finishing && !aiPlaying && !sending){
                        handler.postDelayed(() -> startRecording(),800);
                    }
                    return;
                }

                // 긴 발화는 최대 18초에서 자연스럽게 끊음
                if(speechDetected && elapsed>MAX_RECORD_MS){
                    stopRecordingAndSend();
                    return;
                }

                handler.postDelayed(this,200);
            }
        };
        handler.postDelayed(amplitudeRunnable,200);
    }

    private void stopRecording(){
        if(amplitudeRunnable!=null)handler.removeCallbacks(amplitudeRunnable);
        try{
            if(recorder!=null){
                try{recorder.stop();}catch(Exception ignored){}
                recorder.release();
                recorder=null;
            }
        }catch(Exception ignored){}
        recording=false;
    }

    private void stopRecordingAndSend(){
        if(sending)return;
        if(amplitudeRunnable!=null)handler.removeCallbacks(amplitudeRunnable);
        try{
            if(recorder!=null){
                try{recorder.stop();}catch(Exception ignored){}
                recorder.release();
                recorder=null;
            }
        }catch(Exception ignored){}
        recording=false;

        if(!speechDetected || currentAudioFile==null || !currentAudioFile.exists() || currentAudioFile.length()<3500){
            setCallState("듣는 중");
            setStatus("대기 중");
            setMicHint("말씀이 없거나 너무 짧으면 계속 기다립니다");
            startRecordingAfterAi();
            return;
        }
        sendAudioTurn(currentAudioFile);
    }

    private void sendAudioTurn(File file){
        sending=true;
        setCallState("고객 응답 준비 중");
        setStatus("고객 응답 준비 중");
        setMicHint("잠시만 기다려주세요");
        new Thread(() -> {
            try{
                JSONObject res=uploadAudio("/api/voice/turn",file);
                sending=false;
                String audioUrl=res.optString("audio_url","");
                String reply=res.optString("reply_text","네, 계속 말씀해보세요.");
                boolean useLocal=res.optBoolean("use_local_tts",false);
                playAiResponse(audioUrl,reply,useLocal);
            }catch(Exception e){
                sending=false;
                setStatus("음성 전송 실패: "+e.getMessage());
                playLocalTts("음성 서버 연결이 불안정합니다. 잠시 후 다시 시도해주세요.", true);
            }
        }).start();
    }

    private JSONObject uploadAudio(String path, File file)throws Exception{
        String boundary="AITM"+System.currentTimeMillis();
        URL url=new URL(server()+path);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);

        DataOutputStream out=new DataOutputStream(conn.getOutputStream());
        writeFormField(out,boundary,"session_id",String.valueOf(sessionId));
        writeFileField(out,boundary,"audio",file,"audio/mp4");
        out.writeBytes("--"+boundary+"--\r\n");
        out.flush();
        out.close();

        InputStream is=(conn.getResponseCode()>=200&&conn.getResponseCode()<300)?conn.getInputStream():conn.getErrorStream();
        String text=readAll(is);
        if(conn.getResponseCode()<200||conn.getResponseCode()>=300)throw new RuntimeException(text);
        return new JSONObject(text);
    }

    private void writeFormField(DataOutputStream out,String boundary,String name,String value)throws Exception{
        out.writeBytes("--"+boundary+"\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n");
        out.write(value.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream out,String boundary,String name,File file,String mime)throws Exception{
        out.writeBytes("--"+boundary+"\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\""+name+"\"; filename=\""+file.getName()+"\"\r\n");
        out.writeBytes("Content-Type: "+mime+"\r\n\r\n");
        FileInputStream fis=new FileInputStream(file);
        byte[] buf=new byte[8192];
        int len;
        while((len=fis.read(buf))!=-1)out.write(buf,0,len);
        fis.close();
        out.writeBytes("\r\n");
    }

    private void finishSession(){
        finishing=true;paused=true;
        stopRecording();
        stopPlayback();
        stopAudio();
        long duration=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);
        setCallState("평가 중");
        setStatus("평가 리포트 생성 중");
        setMicHint("훈련을 종료합니다");
        new Thread(() -> {
            try{
                JSONObject body=new JSONObject();
                body.put("session_id",sessionId==null?0:sessionId);
                requestJson("/api/voice/session/end","POST",body);
            }catch(Exception ignored){}
            runOnUiThread(() -> {
                Toast.makeText(this,"훈련 종료 / 통화시간 "+duration+"초",Toast.LENGTH_LONG).show();
                setupPanel.setVisibility(View.VISIBLE);
                callPanel.setVisibility(View.GONE);
            });
            setStatus(duration>=180?"유효 훈련 완료":"3분 미만 종료: 관리자 확인 대상");
        }).start();
    }

    private void stopPlayback(){
        try{
            if(player!=null){
                try{player.stop();}catch(Exception ignored){}
                player.release();
                player=null;
            }
        }catch(Exception ignored){}
        aiPlaying=false;
    }

    private void stopAudio(){
        try{if(localTts!=null)localTts.stop();}catch(Exception ignored){}
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        finishing=true;
        stopRecording();
        stopPlayback();
        stopAudio();
        if(localTts!=null)localTts.shutdown();
    }
}
