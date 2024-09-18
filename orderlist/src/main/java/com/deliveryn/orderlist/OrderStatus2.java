package com.deliveryn.orderlist;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OrderStatus2 extends AppCompatActivity {

    private Button orderCompletedBtn;
    private TextView tvBankName, tvAccountNumber, dMoneyView;
    private ImageView ivAccountNumberCopy;
    private String boss, roomId, orderCompleted;
    private int flag = 0, discountedDeliveryFee, discountedDeliveryFee2, perDeliveryFee;
    private ArrayList<FinalOrderPriceModel> arrayList = new ArrayList<>();
    private FirebaseDatabase database;
    private DatabaseReference databaseReference;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private EditText reportNickname, reportReason;
    private Spinner reportType;
    private String reportNicknameValue, reportRadioBtnValue, typeValue, reporter;
    private RadioGroup radioGroup;
    private LinearLayout linearLayoutReportReasonVisibility;
    private View dialogView, bankDialogView;
    private GridView gv;
    private Intent moveApp;

    private final Integer[] bank = { R.drawable.kakao_bank, R.drawable.nh_bank, R.drawable.kb_bank, R.drawable.hana_bank,
            R.drawable.woori_bank, R.drawable.shinhan_bank, R.drawable.toss, R.drawable.ibk_bank }; // bank_logo_image array
    private final String[] bankNames = { "카카오", "NH농협", "KB국민", "하나", "우리", "신한", "토스", "IBK기업" }; // bank_name_string array
    private final String[] bankPackageNames = { "com.kakaobank.channel", "nh.smart.banking", "com.kbstar.kbbank", "com.hanabank.ebk.channel.android.cpb",
            "com.wooribank.smart.npib", "com.shinhan.sbanking", "viva.republica.toss", "com.ibk.android.ionebank"}; // bank_package_id array

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_status2);

        initViews();
        fetchOrderDetails();

        ivAccountNumberCopy.setOnClickListener(v -> setupAccountNumberCopy());
        orderCompletedBtn.setOnClickListener(v -> setupOrderCompletedButton());
        findViewById(R.id.reportBtn2).setOnClickListener(v -> showReportDialog());
        findViewById(R.id.bank_btn).setOnClickListener(v -> showBankDialog());
    }

    private void initViews() {
        orderCompletedBtn = findViewById(R.id.OrderCompletedBtn);
        recyclerView = findViewById(R.id.recyclerView2);
        tvBankName = findViewById(R.id.tv_bank_name);
        tvAccountNumber = findViewById(R.id.tv_account_number);
        ivAccountNumberCopy = findViewById(R.id.bank_btn);

        recyclerView.setHasFixedSize(true); //리사이클러뷰 기존 성능 강화
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new CustomAdapter2(arrayList, this));

        roomId = getIntent().getStringExtra("roomId");
        database = FirebaseDatabase.getInstance(); // Firebase Database setup
    }

    private void fetchOrderDetails() {
        fetchOrderDetail("bank", tvBankName); //은행 이름 가져오기
        fetchOrderDetail("account number", tvAccountNumber); // 계좌번호 가져오기
        fetchPerDeliveryFee(); // 1인당 배달비 정보 가져오기
    }

    // 은행 이름, 계좌번호 가져오기
    private void fetchOrderDetail(String detailType, TextView textView) {
        database.getReference().child("orderlist").child(roomId).child(detailType).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String value = String.valueOf(task.getResult().getValue());
                textView.setText(value);
            } else {
                Log.e("firebase", "Error getting " + detailType + " data", task.getException());
            }
        });
    }

    // 1인당 배달비 정보 가져오기
    private void fetchPerDeliveryFee() {
        database.getReference().child("orderlist").child(roomId).child("per delivery fee").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                int perDeliveryFee = Integer.parseInt(String.valueOf(task.getResult().getValue()));
                fetchUserOrderPrices(perDeliveryFee); // 사용자별 주문 금액 정보 가져오기
            } else {
                Log.e("firebase", "Error getting per delivery fee", task.getException());
            }
        });
    }

    // 사용자별 주문 금액 정보 가져오기
    private void fetchUserOrderPrices(int perDeliveryFee) {
        databaseReference = database.getReference("orderlist").child(roomId).child("users");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                arrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    OrderInfoModel orderInfo = snapshot.getValue(OrderInfoModel.class);

                    if (orderInfo != null) {
                        int perPrice = Integer.parseInt(orderInfo.getPrice()); // 사용자별 주문 금액
                        String nickname = orderInfo.getNickname(); // 사용자별 닉네임
                        int perTotalPrice = perPrice + perDeliveryFee; // 총 가격 = 1인당 배달비 + 사용자별 주문 금액

                        FinalOrderPriceModel finalOrderPrice = new FinalOrderPriceModel();
                        finalOrderPrice.setUser(nickname);
                        finalOrderPrice.setTotal_price(String.valueOf(perTotalPrice));
                        arrayList.add(finalOrderPrice);
                    }
                }
                recyclerView.getAdapter().notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("OrderStatus2", "Error fetching user order prices", error.toException());
            }
        });
    }

    // 계좌번호 복사 클릭
    private void setupAccountNumberCopy() {
        String accountBankCopyText = tvBankName.getText().toString();
        String accountNumCopyText = tvAccountNumber.getText().toString().replaceAll("-", ""); // 계좌번호에서 "-" 제거

        // 클립보드 사용
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("ID", accountBankCopyText + " " + accountNumCopyText); // 클립보드에 ID라는 이름표로 account_number 값을 복사하여 저장
        clipboardManager.setPrimaryClip(clipData);

        Toast.makeText(OrderStatus2.this, "계좌번호가 복사되었습니다.", Toast.LENGTH_SHORT).show();
    }

    // 주문 완료 버튼 클릭
    private void setupOrderCompletedButton() {
        // 방장 정보 불러오기
        database.getReference().child("orderlist").child(roomId).child("boss").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boss = String.valueOf(task.getResult().getValue());

                // 현재 사용자가 방장인지 확인
                if (boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                    checkAndCompleteOrder(); // 주문 완료 버튼 눌렀는지 확인
                } else {
                    Toast.makeText(OrderStatus2.this, "권한이 없습니다.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("firebase", "Error getting boss data", task.getException());
            }
        });
    }

    private void checkAndCompleteOrder() {
        database.getReference().child("orderlist").child(roomId).child("orderCompleted").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                orderCompleted = String.valueOf(task.getResult().getValue());
                if (orderCompleted.equals("Y")) {
                    Toast.makeText(OrderStatus2.this, "주문 완료 버튼은 한 번만 누를 수 있습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    // 파이어베이스 실시간 데이터베이스에 주문 완료 여부 N->Y로 변경
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderCompleted", "Y");
                    database.getReference().child("orderlist").child(roomId).updateChildren(map);

                    updateBossDiscountedDeliveryFee();
                    notifyParticipants();
                }
            } else {
                Log.e("firebase", "Error checking order completion status", task.getException());
            }
        });
    }

    // 누적 할인 배달비 - 방장
    private void updateBossDiscountedDeliveryFee() {
        database.getReference().child("users").child(boss).child("discounted_delivery_fee").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                discountedDeliveryFee = Integer.parseInt(String.valueOf(task.getResult().getValue())); // 방장의 누적 할인 배달비 DB에서 가져옴

                // 1인당 배달비 정보 가져오기
                database.getReference().child("orderlist").child(roomId).child("per delivery fee").get().addOnCompleteListener(perFeeTask -> {
                    if (perFeeTask.isSuccessful()) {
                        int bossPerDeliveryFee = Integer.parseInt(String.valueOf(perFeeTask.getResult().getValue()));
                        discountedDeliveryFee += bossPerDeliveryFee;
                        database.getReference().child("users").child(boss).child("discounted_delivery_fee").setValue(discountedDeliveryFee);

                        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        View view = inflater.inflate(R.layout.tab_layout, null);
                        dMoneyView = view.findViewById(R.id.discounted_delivery_fee);
                        dMoneyView.setText(String.valueOf(discountedDeliveryFee) + "원");
                    } else {
                        Log.e("firebase", "Error getting per delivery fee", perFeeTask.getException());
                    }
                });
            } else {
                Log.e("firebase", "Error getting boss discounted delivery fee", task.getException());
            }
        });
    }

    // 참여자 - 푸시 알림 전송 & 누적 할인 배달비
    private void notifyParticipants() {
        database.getReference().child("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Boolean> participationUsers = (Map<String, Boolean>) snapshot.getValue();
                if (participationUsers == null || participationUsers.isEmpty()) {
                    Toast.makeText(OrderStatus2.this, "참여자가 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    // 1인당 배달비 가져오기
                    database.getReference().child("orderlist").child(roomId).child("per delivery fee").get().addOnCompleteListener(perFeeTask -> {
                        if (perFeeTask.isSuccessful()) {
                            perDeliveryFee = Integer.parseInt(String.valueOf(perFeeTask.getResult().getValue()));
                            for (String userId : participationUsers.keySet()) {
                                updateParticipantDiscountedDeliveryFee(userId);
                                sendPushNotification(userId);
                            }
                        } else {
                            Log.e("firebase", "Error getting per delivery fee", perFeeTask.getException());
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("firebase", "Error getting participation users", error.toException());
            }
        });
    }

    // 누적 할인 배달비 - 참여자
    private void updateParticipantDiscountedDeliveryFee(String userId) {
        database.getReference().child("users").child(userId).child("discounted_delivery_fee").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                discountedDeliveryFee2 = Integer.parseInt(String.valueOf(task.getResult().getValue())) + perDeliveryFee;
                database.getReference().child("users").child(userId).child("discounted_delivery_fee").setValue(discountedDeliveryFee2);
            } else {
                Log.e("firebase", "Error getting participant discounted delivery fee", task.getException());
            }
        });
    }

    // 참여자들에게 push 알림 전송
    private void sendPushNotification(String userId) {
        database.getReference().child("users").child(userId).child("pushToken").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String pushToken = String.valueOf(task.getResult().getValue());
                sendFCM(pushToken);
                Toast.makeText(OrderStatus2.this, "참여자들에게 푸시 알림을 보냈습니다.", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("firebase", "Error getting push token", task.getException());
            }
        });
    }

    private void showReportDialog() {
        dialogView = LayoutInflater.from(this).inflate(R.layout.report_dialog, null);
        AlertDialog.Builder dlg = new AlertDialog.Builder(this, R.style.MyAlertDialogStyle);
        dlg.setTitle("           <신고할 사용자 정보 입력>");
        dlg.setView(dialogView);

        initializeReportDialogComponents();

        dlg.setPositiveButton("확인", (dialog, which) -> reportConfirmation());
        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    private void initializeReportDialogComponents() {
        radioGroup = dialogView.findViewById(R.id.radioGroup);
        linearLayoutReportReasonVisibility = dialogView.findViewById(R.id.linearLayout_report_reason);
        reportReason = dialogView.findViewById(R.id.report_reason);

        // 신고 유형 - 기타 누르면 신고 사유 visible
        reportType = dialogView.findViewById(R.id.report_list);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.report_spinner_array, android.R.layout.simple_spinner_dropdown_item);

        // 드롭다운 클릭 시 선택 창
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 스피너에 어댑터 설정
        reportType.setAdapter(adapter);

        // 스피너에서 선택 했을 경우 이벤트 처리
        reportType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                // 신고 유형 - 기타 눌렀을 경우
                if ("기타".equals(reportType.getItemAtPosition(position).toString())) {
                    flag = 1;
                    linearLayoutReportReasonVisibility.setVisibility(View.VISIBLE);
                    reportReason.setText(null); // 신고 사유 EditText 초기화
                } else {
                    flag = 0;
                    linearLayoutReportReasonVisibility.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                flag = 0;
                typeValue = reportType.getSelectedItem().toString();
            }
        });

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            reportRadioBtnValue = (checkedId == R.id.report_radioBtn_boss) ? "방장" : "참여자";
        });
    }

    private void reportConfirmation() {
        // 신고할 닉네임
        reportNickname = dialogView.findViewById(R.id.report_nickname);
        reportNicknameValue = reportNickname.getText().toString();

        // 신고 유형
        typeValue = reportType.getSelectedItem().toString();

        // 신고하는 계정 닉네임(현재 사용자)
        reporter = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();

        // 현재 날짜 및 시간
        Object datetime = ServerValue.TIMESTAMP;

        // 신고 사유 있는 경우(= 신고 유형이 기타인 경우)
        // 신고 사유 없는 경우(= 신고 유형이 기타가 아닌 경우)
        String reportReasonValue = flag == 1 ? reportReason.getText().toString() : null;
        insertReport(reporter, datetime, reportRadioBtnValue, reportNicknameValue, typeValue, reportReasonValue);

        Toast.makeText(this, "신고되었습니다.", Toast.LENGTH_SHORT).show();
    }

    public void insertReport(String reporter, Object datetime, String reportedType, String reportedNickname, String reportType, String specific) {
        ReportModel reportModel = new ReportModel(reporter, datetime, reportedType, reportedNickname, reportType, specific);
        DatabaseReference pushRef = database.getReference("report").push();
        pushRef.setValue(reportModel);
    }

    private void showBankDialog() {
        bankDialogView = LayoutInflater.from(this).inflate(R.layout.bank_list, null);
        AlertDialog.Builder bankDlg = new AlertDialog.Builder(this, R.style.MyAlertDialogStyle);
        bankDlg.setView(bankDialogView);

        gv = bankDialogView.findViewById(R.id.gridBank);
        MyGridAdapter gridAdapter = new MyGridAdapter();
        gv.setAdapter(gridAdapter);

        bankDlg.setPositiveButton("확인", (dialog, which) -> Toast.makeText(this, "송금 완료했습니다", Toast.LENGTH_SHORT).show());
        bankDlg.setNegativeButton("취소", null);
        bankDlg.show();
    }

    void sendFCM(String pushToken) {
        Gson gson = new Gson();

        NotificationModel notificationModel = new NotificationModel();
        notificationModel.to = pushToken;
        notificationModel.data.title = "주문 완료!";
        notificationModel.data.text = "방장이 주문을 했습니다.";

        RequestBody requestBody = RequestBody.create(gson.toJson(notificationModel), MediaType.parse("application/json; charset=utf8"));
        Request request = new Request.Builder()
                .header("Content-Type", "application/json")
                .addHeader("Authorization", BuildConfig.FCM_KEY)
                .url("https://fcm.googleapis.com/fcm/send")
                .post(requestBody)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("push 알림: ","실패");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
            }
        });
    }

    public boolean getPackageList(String pkgName) {
        PackageManager pkgManager = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> mApps = pkgManager.queryIntentActivities(mainIntent, 0);

        try {
            for (ResolveInfo app : mApps) {
                if (app.activityInfo.packageName.startsWith(pkgName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    // GirdView Adapter class
    public class MyGridAdapter extends BaseAdapter {
        private final Context context;

        public MyGridAdapter() { context = OrderStatus2.this; }

        @Override
        public int getCount() { return bank.length; }

        @Override
        public Object getItem(int position) { return null; }

        @Override
        public long getItemId(int position) { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.grid_item, parent, false);
            }

            ImageView bankLogo = convertView.findViewById(R.id.bank_logo);
            TextView bankName = convertView.findViewById(R.id.bank_name);

            bankLogo.setImageResource(bank[position]);
            bankName.setText(bankNames[position]);

            final String bankPkg = bankPackageNames[position];
            convertView.setOnClickListener(new View.OnClickListener(){

                @Override
                public void onClick(View v) {
                    if (getPackageList(bankPkg)){
                        moveApp = getPackageManager().getLaunchIntentForPackage(bankPkg);
                        moveApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    } else {
                        String url = "market://details?id=" + bankPkg;
                        moveApp = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    }
                    startActivity(moveApp);
                }
            });

            return convertView;
        }
    }
}