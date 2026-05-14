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

    private LinearLayout setupPanel, callPanel, callWrapper;
    private EditText serverEdit, employeeEdit;
    private Spinner positionSpinner, customerTypeSpinner, difficultySpinner;
    private TextView statusView, callStateView, callMetaView, goalView, timerView, micHintView;
    private Button pauseButton;
    private Button respondButton;

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
    private final int MAX_RECORD_MS = 45000;

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
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        root.setPadding(dp(14), dp(8), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(0, dp(2), 0, dp(8));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("AI TM Trainer");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(30,64,175));
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("v3.4.1 UI 확정 반영 · 첫 번째 시안 적용 완료");
        sub.setTextSize(13);
        sub.setTextColor(Color.rgb(100,116,139));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, 0);
        header.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        ScrollView setupScroll = new ScrollView(this);
        setupScroll.setFillViewport(false);
        setupScroll.setClipToPadding(false);
        setupScroll.setPadding(0, 0, 0, dp(6));
        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(0, 0, 0, dp(18));
        setupScroll.addView(setupPanel, new ScrollView.LayoutParams(-1, -2));
        root.addView(setupScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout welcomeCard = card();
        TextView welcomeTitle = titleText("콜드콜 훈련 시작", 21, Color.rgb(15,23,42));
        TextView welcomeBody = bodyText("실전처럼 연습하고, 바로 피드백을 받으세요.");
        welcomeCard.addView(welcomeTitle);
        welcomeCard.addView(welcomeBody);
        addCard(setupPanel, welcomeCard, 0, dp(10));

        LinearLayout serverCard = card();
        serverCard.addView(titleText("서버 주소", 17, Color.rgb(15,23,42)));
        LinearLayout serverRow = new LinearLayout(this);
        serverRow.setOrientation(LinearLayout.HORIZONTAL);
        serverRow.setGravity(Gravity.CENTER_VERTICAL);
        serverRow.setPadding(0, dp(8), 0, 0);
        serverEdit = input();
        serverEdit.setText(prefs.getString("server_url", "http://172.30.0.53:8031"));
        serverRow.addView(serverEdit, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button loadButton = button("연결 확인", false);
        loadButton.setTextSize(14);
        loadButton.setOnClickListener(v -> { saveServerUrl(); loadInitialData(); });
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(102), dp(50));
        loadLp.leftMargin = dp(8);
        serverRow.addView(loadButton, loadLp);
        serverCard.addView(serverRow);
        addCard(setupPanel, serverCard, 0, dp(10));

        LinearLayout settingCard = card();
        settingCard.addView(titleText("훈련 설정", 17, Color.rgb(15,23,42)));
        positionSpinner = spinner();
        customerTypeSpinner = spinner();
        difficultySpinner = spinner();
        ArrayList<String> levels = new ArrayList<>();
        levels.add("초급");
        levels.add("중급");
        levels.add("고급");
        difficultySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levels));
        settingCard.addView(formRow("포지션", positionSpinner));
        settingCard.addView(formRow("고객유형", customerTypeSpinner));
        settingCard.addView(formRow("난이도", difficultySpinner));
        addCard(setupPanel, settingCard, 0, dp(10));

        LinearLayout employeeCard = card();
        employeeCard.addView(titleText("훈련자 정보", 17, Color.rgb(15,23,42)));
        employeeEdit = input();
        employeeEdit.setHint("예: 김민수");
        employeeEdit.setText(prefs.getString("employee_name", ""));
        employeeCard.addView(formRow("이름", employeeEdit));
        addCard(setupPanel, employeeCard, 0, dp(10));

        LinearLayout goalCard = card();
        goalCard.addView(titleText("이번 세션 목표", 17, Color.rgb(15,23,42)));
        goalView = infoBox("서버 연결 버튼을 눌러 포지션과 고객유형을 불러오세요.");
        LinearLayout.LayoutParams goalLp = new LinearLayout.LayoutParams(-1, -2);
        goalLp.topMargin = dp(8);
        goalCard.addView(goalView, goalLp);
        TextView helperGoal = bodyText("자료 전송 동의 · 팀장 연결 유도 · 재통화 일정 확정 · 반론 대응 연습");
        helperGoal.setPadding(0, dp(8), 0, 0);
        goalCard.addView(helperGoal);
        addCard(setupPanel, goalCard, 0, dp(12));

        Button startButton = button("훈련 시작하기", true);
        startButton.setTextSize(20);
        startButton.setOnClickListener(v -> startSession());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(62));
        startLp.bottomMargin = dp(10);
        setupPanel.addView(startButton, startLp);

        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        Button customerButton = button("고객유형 관리", false);
        Button recordButton = button("훈련 기록", false);
        secondaryRow.addView(customerButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        Space secondaryGap = new Space(this);
        secondaryRow.addView(secondaryGap, new LinearLayout.LayoutParams(dp(10), 1));
        secondaryRow.addView(recordButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        setupPanel.addView(secondaryRow, new LinearLayout.LayoutParams(-1, -2));

        TextView setupHelper = infoBox("음성 녹음 방식: MediaRecorder · 버튼형 응답");
        setupHelper.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams setupHelperLp = new LinearLayout.LayoutParams(-1, -2);
        setupHelperLp.topMargin = dp(10);
        setupPanel.addView(setupHelper, setupHelperLp);

        callWrapper = new LinearLayout(this);
        callWrapper.setOrientation(LinearLayout.VERTICAL);
        callWrapper.setVisibility(View.GONE);
        root.addView(callWrapper, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView callScroll = new ScrollView(this);
        callScroll.setFillViewport(false);
        callScroll.setClipToPadding(false);
        callScroll.setPadding(0, 0, 0, dp(8));
        callPanel = new LinearLayout(this);
        callPanel.setOrientation(LinearLayout.VERTICAL);
        callPanel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        callPanel.setPadding(0, 0, 0, dp(10));
        callScroll.addView(callPanel, new ScrollView.LayoutParams(-1, -2));
        callWrapper.addView(callScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout sessionCard = card();
        sessionCard.addView(titleText("세션 정보", 16, Color.rgb(37,99,235)));
        callMetaView = bodyText("포지션: -\n고객유형: -\n난이도: -\n훈련자: -");
        callMetaView.setTextColor(Color.rgb(15,23,42));
        callMetaView.setTypeface(Typeface.DEFAULT_BOLD);
        callMetaView.setPadding(0, dp(8), 0, 0);
        sessionCard.addView(callMetaView);
        addCard(callPanel, sessionCard, 0, dp(10));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout timeCard = compactCard();
        timeCard.addView(titleText("경과 시간", 15, Color.rgb(37,99,235)));
        timerView = centeredText(38, Color.rgb(15,23,42), true);
        timerView.setText("00:00");
        timerView.setPadding(0, dp(6), 0, 0);
        timeCard.addView(timerView);
        statsRow.addView(timeCard, new LinearLayout.LayoutParams(0, -2, 1));
        Space statsGap = new Space(this);
        statsRow.addView(statsGap, new LinearLayout.LayoutParams(dp(10), 1));
        LinearLayout stateCard = compactCard();
        stateCard.addView(titleText("현재 상태", 15, Color.rgb(13,148,136)));
        callStateView = centeredText(24, Color.rgb(13,148,136), true);
        callStateView.setText("대기 중");
        callStateView.setPadding(0, dp(10), 0, 0);
        stateCard.addView(callStateView);
        statsRow.addView(stateCard, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, -2);
        statsLp.bottomMargin = dp(10);
        callPanel.addView(statsRow, statsLp);

        TextView guide = infoBox("고객 음성을 들은 뒤 상담원이 말하고, 발화를 마치면 아래 답변 받기 버튼을 눌러 응답을 받습니다.");
        guide.setTextSize(15);
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(-1, -2);
        guideLp.bottomMargin = dp(10);
        callPanel.addView(guide, guideLp);

        LinearLayout customerCard = card();
        TextView customerLine = bodyText("고객 음성 재생 후 자동 녹음이 시작됩니다.");
        customerLine.setTextColor(Color.rgb(15,23,42));
        customerCard.addView(customerLine);
        micHintView = infoBox("녹음 대기");
        micHintView.setTextColor(Color.rgb(220,38,38));
        micHintView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(-1, -2);
        micLp.topMargin = dp(8);
        customerCard.addView(micHintView, micLp);
        addCard(callPanel, customerCard, 0, dp(10));

        LinearLayout statusCard = card();
        statusCard.addView(titleText("상태 표시", 16, Color.rgb(13,148,136)));
        statusView = infoBox("상태: 준비 중\n마이크: 대기\n녹음 방식: MediaRecorder\n서버 응답: 확인 전");
        statusView.setTextSize(14);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(8);
        statusCard.addView(statusView, statusLp);
        addCard(callPanel, statusCard, 0, dp(10));

        TextView footerGuide = bodyText("자동 말끝 감지 대신 버튼으로 발화 종료를 확정합니다.");
        footerGuide.setGravity(Gravity.CENTER);
        callPanel.addView(footerGuide, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actionPanel = new LinearLayout(this);
        actionPanel.setOrientation(LinearLayout.VERTICAL);
        actionPanel.setPadding(0, dp(8), 0, 0);
        actionPanel.setBackground(Color.TRANSPARENT);
        callWrapper.addView(actionPanel, new LinearLayout.LayoutParams(-1, -2));

        respondButton = button("답변 받기", true);
        respondButton.setTextSize(22);
        respondButton.setMinHeight(dp(66));
        respondButton.setOnClickListener(v -> manualSendNow());
        LinearLayout.LayoutParams respondLp = new LinearLayout.LayoutParams(-1, dp(68));
        respondLp.bottomMargin = dp(10);
        actionPanel.addView(respondButton, respondLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        actionPanel.addView(row, new LinearLayout.LayoutParams(-1, -2));

        pauseButton = button("일시정지", false);
        pauseButton.setOnClickListener(v -> togglePause());
        row.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(54), 1));

        Space sp = new Space(this);
        row.addView(sp, new LinearLayout.LayoutParams(dp(10), 1));

        Button finishButton = button("종료 · 평가", false);
        finishButton.setTextColor(Color.rgb(220,38,38));
        finishButton.setBackground(dangerOutlineButtonBg());
        finishButton.setOnClickListener(v -> finishSession());
        row.addView(finishButton, new LinearLayout.LayoutParams(0, dp(54), 1));

        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(cardBg(Color.WHITE));
        return l;
    }

    private LinearLayout compactCard() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        l.setBackground(cardBg(Color.WHITE));
        return l;
    }

    private void addCard(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        parent.addView(child, lp);
    }

    private TextView titleText(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(color);
        return v;
    }

    private TextView bodyText(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(Color.rgb(71,85,105));
        v.setLineSpacing(dp(2), 1.0f);
        return v;
    }

    private LinearLayout formRow(String label, View field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextColor(Color.rgb(52,64,84));
        row.addView(labelView, new LinearLayout.LayoutParams(dp(82), -2));
        row.addView(field, new LinearLayout.LayoutParams(0, dp(50), 1));
        return row;
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
    private GradientDrawable buttonBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(37,99,235),Color.rgb(59,130,246)});g.setCornerRadius(dp(16));return g;}
    private GradientDrawable outlineButtonBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(16));g.setStroke(1,Color.rgb(191,219,254));return g;}
    private GradientDrawable dangerOutlineButtonBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(16));g.setStroke(1,Color.rgb(248,113,113));return g;}
    private GradientDrawable infoBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(248,250,252));g.setCornerRadius(dp(16));g.setStroke(1,Color.rgb(226,232,240));return g;}
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
            ((View)setupPanel.getParent()).setVisibility(View.GONE);
            callWrapper.setVisibility(View.VISIBLE);
            setupPanel.setVisibility(View.GONE);
            callPanel.setVisibility(View.VISIBLE);
            callMetaView.setText("포지션: "+selectedPositionName()+"\n고객유형: "+selectedCustomerTypeName()+"\n난이도: "+difficultySpinner.getSelectedItem()+"\n훈련자: "+employee);
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
            setMicHint("말을 마친 뒤 답변 받기 버튼을 누르세요");
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

                if(amp>=AMP_THRESHOLD){
                    voiceHitCount++;
                    silenceHitCount=0;
                    if(voiceHitCount>=2){
                        speechDetected=true;
                        setCallState("상담원 말하는 중");
                        setStatus("녹음 중");
                        setMicHint("말을 마친 뒤 답변 받기 버튼을 누르세요");
                    }
                }

                // 버튼 응답 모드: 침묵만으로 자동 전송하지 않습니다.
                if(speechDetected && elapsed>MAX_RECORD_MS){
                    stopRecordingAndSend();
                    return;
                }

                if(!speechDetected && elapsed>15000){
                    setCallState("듣는 중");
                    setStatus("대기 중");
                    setMicHint("말씀을 시작하면 녹음됩니다");
                }

                handler.postDelayed(this,250);
            }
        };
        handler.postDelayed(amplitudeRunnable,250);
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

    private void manualSendNow(){
        if(paused || finishing || aiPlaying || sending)return;

        if(recording){
            if(!speechDetected){
                setCallState("듣는 중");
                setStatus("대기 중");
                setMicHint("아직 음성이 감지되지 않았습니다. 먼저 말씀해주세요.");
                return;
            }
            stopRecordingAndSend();
            return;
        }

        setCallState("듣는 중");
        setStatus("녹음 시작");
        setMicHint("말을 마친 뒤 답변 받기 버튼을 누르세요");
        startRecording();
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
            setMicHint("짧게 인식되었습니다. 다시 말씀 후 답변 받기 버튼을 눌러주세요.");
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
                ((View)setupPanel.getParent()).setVisibility(View.VISIBLE);
                callWrapper.setVisibility(View.GONE);
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
