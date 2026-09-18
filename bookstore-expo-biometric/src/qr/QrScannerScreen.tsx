import { CameraView, useCameraPermissions } from 'expo-camera';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';

/**
 * Quét QR chứa "verification_uri_complete" (LEGACY) hoặc JSON {apiUrl, sessionId}
 * (KEYCLOAK_SPI) — chỉ decode QR trong khung hình rồi trả rawValue ra ngoài qua
 * onQrDetected, KHÔNG tự xử lý gì ở đây. Tương đương QrScannerScreen.kt.
 */
export function QrScannerScreen({
  onQrDetected,
  onCancel,
}: {
  onQrDetected: (rawValue: string) => void;
  onCancel: () => void;
}) {
  const [permission, requestPermission] = useCameraPermissions();

  // React state (không phải plain ref) vì phải trigger re-render để truyền active={false}
  // xuống CameraView — trên iOS, AVCaptureSession có độ trễ khi dừng sau khi component cha
  // unmount, nên onBarcodeScanned có thể bắn thêm lần nữa với cùng QR trước khi camera
  // thực sự biến mất, tạo ra 2 lần xử lý cho 1 lần quét. Đặt active=false ngay khi phát
  // hiện QR đầu tiên để dừng camera session lập tức, không chỉ dựa vào unmount.
  const [detectedValue, setDetectedValue] = useState<string | null>(null);
  // onBarcodeScanned có thể bắn nhiều lần trong CÙNG một tick trước khi setDetectedValue
  // kịp re-render (native callback, không phải React event) — ref chặn ngay lập tức,
  // trong khi state chỉ dùng để điều khiển active prop.
  const alreadyDetected = useRef(false);

  useEffect(() => {
    if (!permission) return;
    if (!permission.granted && permission.canAskAgain) {
      requestPermission();
    }
  }, [permission, requestPermission]);

  // Gọi onQrDetected (side-effect chạm tới state của component cha) trong effect, KHÔNG
  // phải trực tiếp trong onBarcodeScanned/setState updater — onBarcodeScanned có thể bắn
  // ngay trong lúc React đang render CameraView lần đầu, và dispatch thẳng ở đó vi phạm
  // quy tắc React ("Cannot update a component while rendering a different component"),
  // khiến state bị áp dụng sai thứ tự (chính là nguyên nhân dialog "nhấp nháy" trước đó).
  useEffect(() => {
    if (detectedValue !== null) onQrDetected(detectedValue);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detectedValue]);

  const handleBarcodeScanned = useCallback(({ data }: { data: string }) => {
    if (alreadyDetected.current) return;
    alreadyDetected.current = true;
    setDetectedValue(data);
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.cameraArea}>
        {permission?.granted ? (
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
            active={detectedValue === null}
            barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
            onBarcodeScanned={handleBarcodeScanned}
          />
        ) : (
          <View style={styles.permissionMessage}>
            <Text>Cần quyền camera để quét mã QR</Text>
          </View>
        )}
      </View>

      <View style={styles.footer}>
        <Text style={styles.hint}>Đưa camera vào mã QR hiển thị trên máy tính để đăng nhập</Text>
        <View style={styles.spacer} />
        <Button title="Huỷ" onPress={onCancel} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  cameraArea: { flex: 1 },
  permissionMessage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  footer: { padding: 16 },
  hint: { fontSize: 13, color: '#555' },
  spacer: { height: 8 },
});
