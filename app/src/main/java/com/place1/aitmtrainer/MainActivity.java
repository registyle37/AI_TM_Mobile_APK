package com.place1.aitmtrainer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
import java.util.Random;

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
    private boolean paused = false, aiSpeaking = false, finishing = false, isListening = false, isSending = false;
    private Handler handler = new Handler();
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences prefs;

    private long callStartMillis = 0L;
    private Runnable timerRunnable;
    private Runnable listenTimeoutRunnable;
    private String lastPartialText = "";
    private int listenSeq = 0;
    private int fallbackTurn = 0;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ai_tm_trainer", MODE_PRIVATE);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }

        tts = new TextToSpeech(this, this);
        setupSpeechRecognizer();
        buildUi();
        loadInitialData();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.KOREAN);
            tts.setSpeechRate(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {
                    aiSpeaking = true;
                    stopListening();
                }
                @Override public void onDone(String id) {
                    aiSpeaking = false;
                    if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 1500);
                }
                @Override public void onError(String id) {
                    aiSpeaking = false;
                    if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 1500);
                }
            });
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서 음성인식을 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isListening = true;
                setCallState("듣는 중");
                setStatus("말씀하세요");
                setMicHint("상담원 음성을 듣고 있습니다");
            }
            @Override public void onBeginningOfSpeech() { setMicHint("말씀을 인식하고 있습니다"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                isListening = false;
                setCallState("AI 생각 중");
                setStatus("발화 확정 중");
                setMicHint("잠시만 기다려주세요");
                final int seq = listenSeq;
                handler.postDelayed(() -> {
                    if (seq == listenSeq && !isSending && lastPartialText.trim().length() > 0) {
                        submitRecognizedText(lastPartialText.trim());
                    }
                }, 900);
            }
            @Override public void onError(int error) {
                isListening = false;
                if (paused || finishing || aiSpeaking || isSending) return;

                if (lastPartialText.trim().length() > 0) {
                    submitRecognizedText(lastPartialText.trim());
                    return;
                }

                setCallState("듣는 중");
                setStatus("다시 듣는 중");
                setMicHint("말씀이 없으면 계속 듣고 있습니다");
                handler.postDelayed(() -> startListeningSilently(), 900);
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0).trim() : "";
                if (text.length() == 0) text = lastPartialText.trim();
                if (text.length() > 0) submitRecognizedText(text);
                else if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 900);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    lastPartialText = matches.get(0).trim();
                    if (lastPartialText.length() > 0) {
                        setStatus("음성 인식 중");
                        setMicHint("말씀을 듣고 있습니다");
                    }
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
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
        title.setTextColor(Color.rgb(15, 23, 42));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("포지션과 고객유형을 불러온 뒤 실제 통화처럼 훈련합니다");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(100, 116, 139));
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        setupPanel.setBackground(cardBg(Color.WHITE));
        scroll.addView(setupPanel);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

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
        root.addView(callPanel, new LinearLayout.LayoutParams(-1, 0, 1));

        callMetaView = centeredText(15, Color.rgb(71,85,105), false);
        callPanel.addView(callMetaView);

        timerView = centeredText(42, Color.rgb(37,99,235), true);
        timerView.setText("00:00");
        timerView.setPadding(0, dp(34), 0, dp(8));
        callPanel.addView(timerView);

        callStateView = centeredText(28, Color.rgb(15,23,42), true);
        callStateView.setText("대기 중");
        callPanel.addView(callStateView, new LinearLayout.LayoutParams(-1, -2));

        micHintView = centeredText(15, Color.rgb(100,116,139), false);
        micHintView.setText("통화 준비 중");
        micHintView.setPadding(0, dp(12), 0, dp(28));
        callPanel.addView(micHintView);

        TextView guide = infoBox("텍스트는 표시하지 않습니다.\nAI가 말한 뒤 자동으로 상담원 음성을 듣습니다.\n고객유형별로 다르게 반응합니다.");
        guide.setGravity(Gravity.CENTER);
        callPanel.addView(guide);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(24), 0, 0);

        pauseButton = button("일시정지", false);
        pauseButton.setOnClickListener(v -> togglePause());
        row.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(52), 1));

        Space space = new Space(this);
        row.addView(space, new LinearLayout.LayoutParams(dp(10), 1));

        Button finishButton = button("종료/평가", true);
        finishButton.setOnClickListener(v -> finishSession());
        row.addView(finishButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        callPanel.addView(row, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("상태: 준비 중");
        statusView.setTextColor(Color.rgb(71, 85, 105));
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
        v.setText(s); v.setTextSize(18); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.rgb(15, 23, 42)); v.setPadding(0, dp(22), 0, dp(8));
        return v;
    }
    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(13); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.rgb(52,64,84)); v.setPadding(0, dp(12), 0, dp(4));
        return v;
    }
    private EditText input() {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setTextSize(15); e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(inputBg()); e.setMinHeight(dp(48));
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
        b.setText(text); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(37,99,235));
        b.setBackground(primary ? buttonBg() : outlineButtonBg()); b.setMinHeight(dp(50));
        return b;
    }
    private TextView infoBox(String text) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(14); v.setTextColor(Color.rgb(71,85,105));
        v.setPadding(dp(14),dp(12),dp(14),dp(12)); v.setBackground(infoBg());
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
        URL url = new URL(server() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        if(body != null){
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
        }
        InputStream is = (conn.getResponseCode()>=200 && conn.getResponseCode()<300) ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(is);
        if(conn.getResponseCode()<200 || conn.getResponseCode()>=300) throw new RuntimeException(text);
        return new JSONObject(text);
    }
    private JSONArray requestJsonArray(String path) throws Exception {
        URL url = new URL(server() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        return new JSONArray(readAll(conn.getInputStream()));
    }
    private String readAll(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is,"UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line=br.readLine())!=null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void loadInitialData() {
        new Thread(() -> {
            try {
                saveServerUrl();
                requestJson("/api/health","GET",null);
                positions = requestJsonArray("/api/positions");
                ArrayList<String> names = new ArrayList<>();
                for(int i=0;i<positions.length();i++){
                    JSONObject p=positions.getJSONObject(i);
                    if(p.optInt("is_active",1)==1) names.add(p.getString("name"));
                }
                runOnUiThread(() -> {
                    positionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                    positionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id){
                            try{
                                currentPositionCode = findPositionCodeByName(String.valueOf(positionSpinner.getSelectedItem()));
                                loadCustomerTypes();
                                updateGoalText();
                            }catch(Exception ignored){}
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
                    });
                });
                setStatus("서버 연결 완료");
                loadCustomerTypes();
            } catch(Exception e) {
                setStatus("서버 연결 실패: "+e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "서버 주소와 관리자 서버 실행 상태를 확인하세요.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    private String findPositionCodeByName(String name) throws Exception {
        for(int i=0;i<positions.length();i++){
            JSONObject p=positions.getJSONObject(i);
            if(name.equals(p.getString("name"))) return p.getString("code");
        }
        return "newbie";
    }
    private JSONObject currentPositionObject(){
        try{
            for(int i=0;i<positions.length();i++){
                JSONObject p=positions.getJSONObject(i);
                if(currentPositionCode.equals(p.getString("code"))) return p;
            }
        }catch(Exception ignored){}
        return null;
    }
    private void updateGoalText(){
        try{
            JSONObject p=currentPositionObject();
            if(p==null) return;
            JSONArray goals=p.optJSONArray("goals");
            ArrayList<String> names=new ArrayList<>();
            if(goals!=null){
                for(int i=0;i<goals.length();i++){
                    JSONObject g=goals.getJSONObject(i);
                    if(g.optInt("is_active",1)==1 && g.optInt("is_core",1)==1) names.add(g.optString("title",""));
                }
            }
            runOnUiThread(() -> goalView.setText("목표: "+join(names," · ")));
        }catch(Exception ignored){}
    }
    private String join(ArrayList<String> arr,String sep){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<arr.size();i++){
            if(i>0) sb.append(sep);
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
                    if(item.optInt("is_active",1)==1) names.add(item.getString("name"));
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
    private int selectedCustomerTypeId() throws Exception {
        String selectedName=String.valueOf(customerTypeSpinner.getSelectedItem());
        for(int i=0;i<customerTypes.length();i++){
            JSONObject item=customerTypes.getJSONObject(i);
            if(selectedName.equals(item.getString("name"))) return item.getInt("id");
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
        if(positionSpinner.getSelectedItem()==null || customerTypeSpinner.getSelectedItem()==null){
            Toast.makeText(this,"서버 연결 버튼으로 포지션/유형을 먼저 불러오세요.",Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("employee_name",employee).putString("server_url",server()).apply();
        paused=false; finishing=false; sessionId=null; fallbackTurn=0;
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
                body.put("customer_type_name",selectedCustomerTypeName());
                body.put("difficulty",String.valueOf(difficultySpinner.getSelectedItem()));
                JSONObject res=requestJson("/api/sessions/start","POST",body);
                sessionId=res.getInt("session_id");
                speakAi(res.getString("first_message"));
            }catch(Exception e){
                sessionId=-1;
                setStatus("서버 대화 API 연결 실패, 앱 내 응답 사용");
                speakAi(fallbackFirstMessage());
            }
        }).start();
    }
    private String fallbackFirstMessage(){
        String name=selectedCustomerTypeName();
        if(name.contains("바쁜")) return "네, 지금 바쁜데요.";
        if(name.contains("의심")) return "네, 말씀하세요.";
        if(name.contains("가격")) return "여보세요, 네.";
        return "여보세요?";
    }
    private void startTimer(){
        if(timerRunnable!=null) handler.removeCallbacks(timerRunnable);
        timerRunnable=new Runnable(){
            @Override public void run(){
                long sec=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);
                timerView.setText(String.format(Locale.KOREA,"%02d:%02d",sec/60,sec%60));
                if(!finishing) handler.postDelayed(this,1000);
            }
        };
        handler.post(timerRunnable);
    }
    private void togglePause(){
        paused=!paused;
        if(paused){
            stopListening();
            setCallState("일시정지");
            setStatus("일시정지됨");
            setMicHint("다시 시작을 누르면 이어집니다");
            pauseButton.setText("다시 시작");
        }else{
            pauseButton.setText("일시정지");
            setStatus("다시 듣기 시작");
            startListeningSilently();
        }
    }
    private void startListeningSilently(){
        if(paused || finishing || aiSpeaking || speechRecognizer==null || isSending) return;
        try{
            stopListening();
            lastPartialText="";
            isListening=true;
            listenSeq++;
            final int seq=listenSeq;
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2500);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,2200);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,3000);
            speechRecognizer.startListening(intent);
            if(listenTimeoutRunnable!=null) handler.removeCallbacks(listenTimeoutRunnable);
            listenTimeoutRunnable = () -> {
                if(seq == listenSeq && isListening && !paused && !finishing && !aiSpeaking && !isSending){
                    if(lastPartialText.trim().length()>0) submitRecognizedText(lastPartialText.trim());
                    else{
                        stopListening();
                        setCallState("듣는 중");
                        setStatus("다시 듣는 중");
                        handler.postDelayed(() -> startListeningSilently(),700);
                    }
                }
            };
            handler.postDelayed(listenTimeoutRunnable, 8000);
        }catch(Exception e){
            setStatus("음성 인식 재시도 중");
            handler.postDelayed(() -> startListeningSilently(),1200);
        }
    }
    private void stopListening(){
        try{
            if(listenTimeoutRunnable!=null) handler.removeCallbacks(listenTimeoutRunnable);
            if(speechRecognizer!=null) speechRecognizer.cancel();
        }catch(Exception ignored){}
        isListening=false;
    }
    private void submitRecognizedText(String text){
        if(text==null) return;
        text=text.trim();
        if(text.length()==0) return;
        if(isSending || paused || finishing || aiSpeaking) return;
        isSending=true;
        stopListening();
        sendEmployeeMessage(text);
    }
    private void sendEmployeeMessage(String text){
        setCallState("AI 생각 중");
        setStatus("AI 고객 답변 생성 중");
        setMicHint("AI가 답변을 준비하고 있습니다");
        new Thread(() -> {
            try{
                JSONObject body=new JSONObject();
                body.put("session_id",sessionId == null ? -1 : sessionId);
                body.put("message",text);
                body.put("position_code",currentPositionCode);
                body.put("customer_type_name",selectedCustomerTypeName());
                body.put("difficulty",String.valueOf(difficultySpinner.getSelectedItem()));
                JSONObject res=requestJson("/api/chat","POST",body);
                String reply=res.optString("reply","");
                if(reply.trim().length()==0 || reply.contains("구체적으로") || reply.contains("어떤 내용인지")) {
                    reply = fallbackReply(text);
                }
                isSending=false;
                speakAi(reply);
            }catch(Exception e){
                isSending=false;
                speakAi(fallbackReply(text));
            }
        }).start();
    }

    private boolean hasAny(String text, String[] arr){
        String m=text.replace(" ","").toLowerCase();
        for(String a:arr) if(m.contains(a)) return true;
        return false;
    }
    private String pick(String[] arr){return arr[random.nextInt(arr.length)];}

    private String fallbackReply(String text){
        fallbackTurn++;
        String type=selectedCustomerTypeName();
        String m=text.replace(" ","").toLowerCase();

        boolean material=hasAny(m,new String[]{"자료","문자","카톡","보내","메일","링크"});
        boolean manager=hasAny(m,new String[]{"팀장","연결","상급자","담당자"});
        boolean recall=hasAny(m,new String[]{"내일","오후","오전","다시","재통화","시간","일정","나중"});
        boolean price=hasAny(m,new String[]{"비용","가격","얼마","견적","돈","무료","비싸"});
        boolean trust=hasAny(m,new String[]{"효과","성과","보장","믿","신뢰","전에","해봤","광고","대행"});
        boolean busy=hasAny(m,new String[]{"바빠","시간없","끊","문자로","나중에"});
        boolean reject=hasAny(m,new String[]{"안해","필요없","괜찮","됐어요","관심없"});

        if(material) return pick(new String[]{
            "자료로 먼저 보실 수 있다는 말씀이세요? 그럼 제가 뭘 중점적으로 보면 되는지만 짧게 알려주세요.",
            "문자로 보내주시면 보긴 할게요. 그런데 자료 보고 나서 다시 통화는 언제 하실 건가요?",
            "자료만 보내는 거면 괜찮습니다. 다만 내용이 너무 광고 같으면 안 볼 수도 있어요."
        });
        if(manager) return pick(new String[]{
            "팀장님이 짧게 설명해주시는 거면 지금 1분 정도는 괜찮습니다.",
            "팀장님은 어떤 부분을 더 정확하게 설명해주시는 건가요?",
            "그럼 팀장님 연결 전에 핵심만 먼저 말씀해주세요."
        });
        if(recall) return pick(new String[]{
            "그럼 정확히 언제 다시 연락 주실 건가요? 시간까지 정해주시면 좋겠습니다.",
            "내일이면 오전이 나으세요, 오후가 나으세요?",
            "나중에라고 하면 놓칠 수 있으니까 시간 하나만 정하고 넘어가도 될까요?"
        });
        if(busy) return pick(new String[]{
            "지금 바빠서 길게는 어렵습니다. 핵심만 짧게 말씀해주실 수 있나요?",
            "그럼 20초 안에 핵심만 말씀해주세요. 길면 끊어야 할 것 같아요.",
            "문자로만 받을지, 짧게 설명을 들을지 둘 중 하나로 해주세요."
        });
        if(reject) return pick(new String[]{
            "지금은 필요 없을 것 같은데요. 그래도 꼭 봐야 하는 이유가 있나요?",
            "관심이 크진 않은데, 자료만 봐도 되는 수준인가요?",
            "그럼 이번에는 안 하는 쪽으로 생각해도 될까요?"
        });
        if(price || type.contains("가격")) return pick(new String[]{
            "비용이 드는 거면 대략적인 범위는 알아야 판단할 수 있을 것 같습니다.",
            "다른 데랑 비교해야 해서요. 가격 말고 어떤 차이가 있는지도 같이 알려주세요.",
            "효과를 확인할 수 있는 기준이 있으면 비용 얘기도 들어볼 수는 있습니다."
        });
        if(trust || type.contains("의심")) return pick(new String[]{
            "예전에 광고를 해봤는데 효과를 잘 못 봐서요. 이번에는 뭘 보고 판단하면 되나요?",
            "다 처음에는 된다고 하더라고요. 확인 가능한 자료나 기준이 있나요?",
            "무조건 진행하라는 게 아니라 먼저 확인해보는 구조라면 설명은 들어볼 수 있습니다."
        });
        if(type.contains("바쁜")) return pick(new String[]{
            "핵심만 짧게 말씀해주세요. 길게는 어렵습니다.",
            "그래서 제가 지금 바로 확인해야 하는 포인트가 뭔가요?",
            "자료로 받을 수 있으면 자료로 받고, 아니면 시간을 다시 잡아야 할 것 같습니다."
        });

        return pick(new String[]{
            "그럼 제 상황에서는 자료를 먼저 보는 게 맞나요, 아니면 짧게 설명을 듣는 게 맞나요?",
            "말씀은 이해했습니다. 제가 판단하려면 효과 확인 기준이랑 진행 방식이 궁금합니다.",
            "좋습니다. 그러면 다음 단계는 자료 확인인지, 팀장 설명인지, 재통화 일정인지 정해주시면 될 것 같습니다."
        });
    }

    private void speakAi(String text){
        runOnUiThread(() -> {
            aiSpeaking=true;
            isSending=false;
            stopListening();
            setCallState("AI 고객 말하는 중");
            setStatus("AI 고객 응답 중");
            setMicHint("AI 고객이 말하고 있습니다");
            tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"ai_customer_"+System.currentTimeMillis());
        });
    }
    private void finishSession(){
        finishing=true; paused=true; stopListening(); stopAudio();
        long duration=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);
        setCallState("평가 중");
        setStatus("평가 리포트 생성 중");
        setMicHint("훈련을 종료합니다");
        new Thread(() -> {
            try{
                if(sessionId!=null && sessionId>0) requestJson("/api/sessions/"+sessionId+"/finish","POST",new JSONObject());
            }catch(Exception ignored){}
            runOnUiThread(() -> {
                Toast.makeText(this,"훈련 종료 / 통화시간 "+duration+"초",Toast.LENGTH_LONG).show();
                setupPanel.setVisibility(View.VISIBLE);
                callPanel.setVisibility(View.GONE);
            });
            setStatus(duration>=180 ? "유효 훈련 완료" : "3분 미만 종료: 관리자 확인 대상");
        }).start();
    }
    private void stopAudio(){try{if(tts!=null) tts.stop();}catch(Exception ignored){}}
    @Override protected void onDestroy(){
        super.onDestroy();
        finishing=true;
        stopListening();
        if(speechRecognizer!=null){
            speechRecognizer.destroy();
            speechRecognizer=null;
        }
        stopAudio();
        if(tts!=null) tts.shutdown();
    }
}
