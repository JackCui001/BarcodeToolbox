package com.jackcui.barcodesc;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanFrame;
import com.huawei.hms.ml.scan.HmsScanFrameOptions;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /**
     * Define requestCode.
     */
    static final int MULTIPLE_FILE_CHOICE_REQ_CODE = 1;
    static final int REQUEST_PERM_REQ_CODE = 2;
    static final int CAM_SCAN_REQ_CODE = 3;
    static final String TAG = "BarcodeScanner";
    static final String WAIT_FOR_SCAN = "等待识别";
    static final String CLEARED_WAIT_FOR_SCAN = "已清空 - 等待识别";
    static final String INVOKED_BY_INTENT_VIEW = "【由外部应用打开文件调用】";
    static final String INVOKED_BY_INTENT_SEND = "【由外部应用分享文件调用】";

    int scanCnt = 0;
    TextView tv;
    Button clearTextBtn, selectPicBtn, scanBtn;
    CheckBox disableParse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = findViewById(R.id.textView);
        clearTextBtn = findViewById(R.id.clearTextButton);
        selectPicBtn = findViewById(R.id.selectPicButton);
        scanBtn = findViewById(R.id.scanButton);
        disableParse = findViewById(R.id.disableParse);

        tv.setText(WAIT_FOR_SCAN);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Log.d(TAG, intent.toString());
        if (type != null) {
            if (!type.startsWith("image/")) {
                Toast.makeText(this, "选择了错误的文件类型！", Toast.LENGTH_LONG).show();
            } else if (action.equals(Intent.ACTION_SEND)) {
                tv.setText(INVOKED_BY_INTENT_SEND);
                handleSendImage(intent);    // Handle single image being sent
            } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                tv.setText(INVOKED_BY_INTENT_SEND);
                handleSendMultipleImages(intent);   // Handle multiple images being sent
            } else if (action.equals(Intent.ACTION_VIEW)) {
                tv.setText(INVOKED_BY_INTENT_VIEW);
                handleViewImage(intent);    // Handle single image being viewed
            }
        }

        clearTextBtn.setOnClickListener(view -> {
            tv.setText(CLEARED_WAIT_FOR_SCAN);
            Log.d(TAG, "文本栏已清空");
            scanCnt = 0;
        });

        selectPicBtn.setOnClickListener(view -> {
            Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
            chooseFile.setType("image/*");  //选择图片
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); //设置可以多选文件
            Intent chooser = Intent.createChooser(chooseFile, "选择图片");
            startActivityForResult(chooser, MULTIPLE_FILE_CHOICE_REQ_CODE);
        });

        scanBtn.setOnClickListener(view -> {
            requestPermission();
        });
    }

    /**
     * Apply for permissions.
     */
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES},
                    REQUEST_PERM_REQ_CODE);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERM_REQ_CODE);
        }
    }

    void scanPic(Uri uri) {
        Bitmap img;
        try {
            img = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片读取失败！", Toast.LENGTH_LONG).show();
            return;
        }
        HmsScanFrame frame = new HmsScanFrame(img);
        HmsScanFrameOptions option = new HmsScanFrameOptions
                .Creator()
                .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                .setMultiMode(true)
                .setPhotoMode(true)
                .setParseResult(true)   // 默认值应为false，华为API文档有误
                .create();
        HmsScan[] results = ScanUtil.decode(this, frame, option).getHmsScans();
        if (results == null || results.length == 0) {
            Toast.makeText(this, "未检测到条形码！", Toast.LENGTH_LONG).show();
            return;
        }
        if (results.length == 1) {
            printResult(results[0]);
        } else {
            printResults(results);
        }
    }

    void scanPics(List<Uri> uris) {
        for (Uri uri : uris) {
            scanPic(uri);
        }
    }

    void handleViewImage(Intent intent) {
        Uri imgUri = intent.getData();
        if (imgUri != null) {
            scanPic(imgUri);
        }
    }

    void handleSendImage(Intent intent) {
        Uri imgUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imgUri != null) {
            scanPic(imgUri);
        }
    }

    void handleSendMultipleImages(Intent intent) {
        ArrayList<Uri> imgUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imgUris != null) {
            scanPics(imgUris);
        }
    }

    String concatCodeInfo(HmsScan res) {
        boolean parse = !disableParse.isChecked();
        final int scanType = res.getScanType();
        final int scanTypeForm = res.getScanTypeForm();
        StringBuilder newText = new StringBuilder();
        if (scanType == HmsScan.QRCODE_SCAN_TYPE) {
            newText.append("QR 码 - ");
        } else if (scanType == HmsScan.AZTEC_SCAN_TYPE) {
            newText.append("AZTEC 码 - ");
        } else if (scanType == HmsScan.DATAMATRIX_SCAN_TYPE) {
            newText.append("Data Matrix 码 - ");
        } else if (scanType == HmsScan.PDF417_SCAN_TYPE) {
            newText.append("PDF417 码 - ");
        } else if (scanType == HmsScan.CODE93_SCAN_TYPE) {
            newText.append("Code93 码 - ");
        } else if (scanType == HmsScan.CODE39_SCAN_TYPE) {
            newText.append("Code39 码 - ");
        } else if (scanType == HmsScan.CODE128_SCAN_TYPE) {
            newText.append("Code128 码 - ");
        } else if (scanType == HmsScan.EAN13_SCAN_TYPE) {
            newText.append("EAN13 码 - ");
        } else if (scanType == HmsScan.EAN8_SCAN_TYPE) {
            newText.append("EAN8 码 - ");
        } else if (scanType == HmsScan.ITF14_SCAN_TYPE) {
            newText.append("ITF14 码 - ");
        } else if (scanType == HmsScan.UPCCODE_A_SCAN_TYPE) {
            newText.append("UPC_A 码 - ");
        } else if (scanType == HmsScan.UPCCODE_E_SCAN_TYPE) {
            newText.append("UPC_E 码 - ");
        } else if (scanType == HmsScan.CODABAR_SCAN_TYPE) {
            newText.append("Codabar 码 - ");
        } else if (scanType == HmsScan.WX_SCAN_TYPE) {
            newText.append("微信码");
        } else if (scanType == HmsScan.MULTI_FUNCTIONAL_SCAN_TYPE) {
            newText.append("多功能码");
        }
        if (scanTypeForm == HmsScan.ARTICLE_NUMBER_FORM) {
            newText.append("产品信息：");
            newText.append(res.getOriginalValue());
            newText.append("\n");
        } else if (scanTypeForm == HmsScan.CONTACT_DETAIL_FORM) {
            newText.append("联系人：\n");
            if (parse) {
                var tmp = res.getContactDetail();
                var peopleName = tmp.getPeopleName();
                var tels = tmp.getTelPhoneNumbers();
                var emailContentList = tmp.getEmailContents();
                var contactLinks = tmp.getContactLinks();
                var company = tmp.getCompany();
                var title = tmp.getTitle();
                var addrInfoList = tmp.getAddressesInfos();
                var note = tmp.getNote();
                if (peopleName != null) {
                    newText.append("姓名：");
                    newText.append(peopleName.getFullName());
                    newText.append("\n");
                }
                if (tels != null && tels.size() != 0) {
                    newText.append("电话：");
                    for (var tel : tels) {
                        var telType = tel.getUseType();
                        if (telType == HmsScan.TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE) {
                            newText.append("手机 - ");
                        } else if (telType == HmsScan.TelPhoneNumber.RESIDENTIAL_USE_TYPE) {
                            newText.append("住家 - ");
                        } else if (telType == HmsScan.TelPhoneNumber.OFFICE_USE_TYPE) {
                            newText.append("工作 - ");
                        } else if (telType == HmsScan.TelPhoneNumber.FAX_USE_TYPE) {
                            newText.append("传真 - ");
                        } else if (telType == HmsScan.TelPhoneNumber.OTHER_USE_TYPE) {
                            newText.append("其他 - ");
                        }
                        newText.append(tel.getTelPhoneNumber());
                        newText.append("\n");
                    }
                }
                if (emailContentList != null && emailContentList.size() != 0) {
                    newText.append("邮箱：");
                    ArrayList<String> emails = new ArrayList<>();
                    for (var email : emailContentList) {
                        emails.add(email.getAddressInfo());
                    }
                    newText.append(com.huawei.hms.framework.common.StringUtils.collection2String(emails));
                    newText.append("\n");
                }
                if (contactLinks != null && contactLinks.length != 0) {
                    newText.append("URL：");
                    newText.append(com.huawei.hms.framework.common.StringUtils.collection2String(Arrays.asList(contactLinks)));
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(company)) {
                    newText.append("公司：");
                    newText.append(company);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(title)) {
                    newText.append("职位：");
                    newText.append(title);
                    newText.append("\n");
                }
                if (addrInfoList != null && addrInfoList.size() != 0) {
                    newText.append("地址：");
                    for (var addrInfo : addrInfoList) {
                        var addrType = addrInfo.getAddressType();
                        if (addrType == HmsScan.AddressInfo.RESIDENTIAL_USE_TYPE) {
                            newText.append("住家 - ");
                        } else if (addrType == HmsScan.AddressInfo.OFFICE_TYPE) {
                            newText.append("工作 - ");
                        } else if (addrType == HmsScan.AddressInfo.OTHER_USE_TYPE) {
                            newText.append("其他 - ");
                        }
                        newText.append(com.huawei.hms.framework.common.StringUtils.collection2String(Arrays.asList(addrInfo.getAddressDetails())));
                        newText.append("\n");
                    }
                }
                if (!StringUtils.isEmpty(note)) {
                    newText.append("备注：");
                    newText.append(note);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.DRIVER_INFO_FORM) {
            newText.append("驾照信息：\n");
            if (parse) {
                var tmp = res.getDriverInfo();
                var familyName = tmp.getFamilyName();
                var middleName = tmp.getMiddleName();
                var givenName = tmp.getGivenName();
                var sex = tmp.getSex();
                var dateOfBirth = tmp.getDateOfBirth();
                var countryOfIssue = tmp.getCountryOfIssue();
                var certType = tmp.getCertificateType();
                var certNum = tmp.getCertificateNumber();
                var dateOfIssue = tmp.getDateOfIssue();
                var dateOfExpire = tmp.getDateOfExpire();
                var province = tmp.getProvince();
                var city = tmp.getCity();
                var avenue = tmp.getAvenue();
                var zipCode = tmp.getZipCode();
                if (!StringUtils.isEmpty(familyName)) {
                    newText.append("姓：");
                    newText.append(familyName);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(middleName)) {
                    newText.append("中间名：");
                    newText.append(middleName);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(givenName)) {
                    newText.append("名：");
                    newText.append(givenName);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(sex)) {
                    newText.append("性别：");
                    newText.append(sex);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(dateOfBirth)) {
                    newText.append("出生日期：");
                    newText.append(dateOfBirth);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(countryOfIssue)) {
                    newText.append("驾照发放国：");
                    newText.append(countryOfIssue);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(certType)) {
                    newText.append("驾照类型：");
                    newText.append(certType);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(certNum)) {
                    newText.append("驾照号码：");
                    newText.append(certNum);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(dateOfIssue)) {
                    newText.append("发证日期：");
                    newText.append(dateOfIssue);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(dateOfExpire)) {
                    newText.append("过期日期：");
                    newText.append(dateOfExpire);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(province)) {
                    newText.append("省/州：");
                    newText.append(province);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(city)) {
                    newText.append("城市：");
                    newText.append(city);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(avenue)) {
                    newText.append("街道：");
                    newText.append(avenue);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(zipCode)) {
                    newText.append("邮政编码：");
                    newText.append(zipCode);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.EMAIL_CONTENT_FORM) {
            newText.append("邮件信息：\n");
            if (parse) {
                var email = res.getEmailContent();
                var addrInfo = email.getAddressInfo();
                var subjectInfo = email.getSubjectInfo();
                var bodyInfo = email.getBodyInfo();
                if (!StringUtils.isEmpty(addrInfo)) {
                    newText.append("收件邮箱：");
                    newText.append(addrInfo);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(subjectInfo)) {
                    newText.append("标题：");
                    newText.append(subjectInfo);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(bodyInfo)) {
                    newText.append("内容：");
                    newText.append(bodyInfo);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.EVENT_INFO_FORM) {
            newText.append("日历事件：\n");
            if (parse) {
                var tmp = res.getEventInfo();
                var abstractInfo = tmp.getAbstractInfo();
                var theme = tmp.getTheme();
                var beginTimeInfo = tmp.getBeginTime();
                var closeTimeInfo = tmp.getCloseTime();
                var sponsor = tmp.getSponsor();
                var placeInfo = tmp.getPlaceInfo();
                var condition = tmp.getCondition();
                if (!StringUtils.isEmpty(abstractInfo)) {
                    newText.append("描述：");
                    newText.append(abstractInfo);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(theme)) {
                    newText.append("摘要：");
                    newText.append(theme);
                    newText.append("\n");
                }
                if (beginTimeInfo != null) {
                    newText.append("开始时间：");
                    newText.append(beginTimeInfo.originalValue);
                    newText.append("\n");
                }
                if (closeTimeInfo != null) {
                    newText.append("开始时间：");
                    newText.append(closeTimeInfo.originalValue);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(sponsor)) {
                    newText.append("组织者：");
                    newText.append(sponsor);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(placeInfo)) {
                    newText.append("地点：");
                    newText.append(placeInfo);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(condition)) {
                    newText.append("状态：");
                    newText.append(condition);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.ISBN_NUMBER_FORM) {
            newText.append("ISBN 号：\n");
            newText.append(res.getOriginalValue());
            newText.append("\n");
        } else if (scanTypeForm == HmsScan.LOCATION_COORDINATE_FORM) {
            newText.append("位置信息：\n");
            if (parse) {
                var tmp = res.getLocationCoordinate();
                var latitude = tmp.getLatitude();
                var longitude = tmp.getLongitude();
                newText.append("经度：");
                newText.append(longitude);
                newText.append("\n");
                newText.append("纬度：");
                newText.append(latitude);
                newText.append("\n");
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.PURE_TEXT_FORM) {
            newText.append("文本：\n");
            newText.append(res.getOriginalValue());
            newText.append("\n");
        } else if (scanTypeForm == HmsScan.SMS_FORM) {
            newText.append("短信：\n");
            if (parse) {
                var tmp = res.getSmsContent();
                var destPhoneNumber = tmp.getDestPhoneNumber();
                var msgContent = tmp.getMsgContent();
                if (!StringUtils.isEmpty(destPhoneNumber)) {
                    newText.append("收件号码：");
                    newText.append(destPhoneNumber);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(msgContent)) {
                    newText.append("内容：");
                    newText.append(msgContent);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.TEL_PHONE_NUMBER_FORM) {
            newText.append("电话号码：\n");
            if (parse) {
                var tmp = res.getTelPhoneNumber();
                if (tmp != null) {
                    var telType = tmp.getUseType();
                    if (telType == HmsScan.TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE) {
                        newText.append("手机：");
                    } else if (telType == HmsScan.TelPhoneNumber.RESIDENTIAL_USE_TYPE) {
                        newText.append("住家：");
                    } else if (telType == HmsScan.TelPhoneNumber.OFFICE_USE_TYPE) {
                        newText.append("工作：");
                    } else if (telType == HmsScan.TelPhoneNumber.FAX_USE_TYPE) {
                        newText.append("传真：");
                    } else if (telType == HmsScan.TelPhoneNumber.OTHER_USE_TYPE) {
                        newText.append("其他：");
                    }
                    newText.append(tmp.getTelPhoneNumber());
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.URL_FORM) {
            newText.append("URL 链接：\n");
            if (parse) {
                var tmp = res.getLinkUrl();
                var theme = tmp.getTheme();
                var linkValue = tmp.getLinkValue();
                if (!StringUtils.isEmpty(theme)) {
                    newText.append("标题：");
                    newText.append(theme);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(linkValue)) {
                    newText.append("链接：");
                    newText.append(linkValue);
                    newText.append("\n");
                }
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.WIFI_CONNECT_INFO_FORM) {
            newText.append("Wi-Fi 信息：\n");
            if (parse) {
                var tmp = res.getWiFiConnectionInfo();
                var ssid = tmp.getSsidNumber();
                var pwd = tmp.getPassword();
                var cipherMode = tmp.getCipherMode();
                if (!StringUtils.isEmpty(ssid)) {
                    newText.append("SSID (网络名称)：");
                    newText.append(ssid);
                    newText.append("\n");
                }
                if (!StringUtils.isEmpty(pwd)) {
                    newText.append("密码：");
                    newText.append(pwd);
                    newText.append("\n");
                }
                newText.append("加密方式：");
                if (cipherMode == HmsScan.WiFiConnectionInfo.WPA_MODE_TYPE) {
                    newText.append("WPA*");
                } else if (cipherMode == HmsScan.WiFiConnectionInfo.WEP_MODE_TYPE) {
                    newText.append("WEP");
                } else if (cipherMode == HmsScan.WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE) {
                    newText.append("开放");
                } else if (cipherMode == HmsScan.WiFiConnectionInfo.SAE_MODE_TYPE) {
                    newText.append("WPA3-SAE");
                }
                newText.append("\n");
                newText.append("隐藏网络：");
                if (StringUtils.indexOfIgnoreCase(res.getOriginalValue(), "H:true") != -1) {
                    newText.append("是");
                } else {
                    newText.append("否");
                }
                newText.append("\n");
            } else {
                newText.append(res.getOriginalValue());
                newText.append("\n");
            }
        } else if (scanTypeForm == HmsScan.OTHER_FORM) {
            newText.append("其他信息：\n");
            newText.append(res.getOriginalValue());
            newText.append("\n");
        }
        return newText.toString();
    }

    void printResult(HmsScan res) {
        StringBuilder newText = new StringBuilder();
        String curText = tv.getText().toString();
        if (!curText.equals(WAIT_FOR_SCAN) && !curText.equals(CLEARED_WAIT_FOR_SCAN)) {
            newText.append(curText);
            newText.append("\n");
        }
        newText.append("---------- 第 ");
        newText.append(++scanCnt);
        newText.append(" 次识别 ----------\n");
        newText.append(concatCodeInfo(res));
        tv.setText(newText.toString());
    }

    void printResults(HmsScan[] results) {
        int n = results.length;
        StringBuilder newText = new StringBuilder();
        String curText = tv.getText().toString();
        if (!curText.equals(WAIT_FOR_SCAN) && !curText.equals(CLEARED_WAIT_FOR_SCAN)) {
            newText.append(curText);
            newText.append("\n");
        }
        newText.append("---------- 第 ");
        newText.append(++scanCnt);
        newText.append(" 次识别 ----------\n");
        newText.append("检测到多码，数量：");
        newText.append(n);
        newText.append("\n");
        for (int i = 0; i < n; i++) {
            newText.append("---------- 码 ");
            newText.append(i + 1);
            newText.append(" ----------\n");
            newText.append(concatCodeInfo(results[i]));
        }
        tv.setText(newText.toString());
    }

    /**
     * Call back the permission application result. If the permission application is successful, the barcode scanning view will be displayed.
     *
     * @param requestCode   Permission application code.
     * @param permissions   Permission array.
     * @param grantResults: Permission application result array.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length < 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted: false");
            Toast.makeText(this, "权限授予失败，请重试！", Toast.LENGTH_LONG).show();
            return;
        }
        // Default View Mode
        if (requestCode == REQUEST_PERM_REQ_CODE) {
            ScanUtil.startScan(this, CAM_SCAN_REQ_CODE, null);
        }
    }

    /**
     * Event for receiving the activity result.
     *
     * @param requestCode Request code.
     * @param resultCode  Result code.
     * @param data        Result.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == CAM_SCAN_REQ_CODE) {
            HmsScan res = data.getParcelableExtra(ScanUtil.RESULT);
            if (res != null) {
                printResult(res);
            }
        }
        if (requestCode == MULTIPLE_FILE_CHOICE_REQ_CODE) {
            ClipData cd = data.getClipData();
            boolean multiFile = cd != null;
            if (multiFile) {
                for (int i = 0; i < cd.getItemCount(); i++) {
                    var item = cd.getItemAt(i);
                    scanPic(item.getUri());
                }
            } else {
                scanPic(data.getData());
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        overrideFontScale(newBase);
    }

    /**
     * 重置配置 fontScale：保持字体比例不变，始终为 1.
     */
    private void overrideFontScale(Context context) {
        if (context == null) {
            return;
        }
        Configuration configuration = context.getResources().getConfiguration();
        configuration.fontScale = 1f;
        applyOverrideConfiguration(configuration);
    }
}