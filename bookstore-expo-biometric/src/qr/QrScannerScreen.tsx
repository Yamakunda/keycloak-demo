import { CameraView, useCameraPermissions } from 'expo-camera';
import React, { useCallback, useEffect, useRef } from 'react';
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

  // Plain ref (không phải React state) vì onBarcodeScanned có thể bắn nhiều lần trước khi
  // component cha unmount camera — tương đương booleanArrayOf(false) trong Kotlin, chỉ cần
  // chặn xử lý > 1 lần, không cần re-render.
  const alreadyDetected = useRef(false);

  useEffect(() => {
    if (!permission) return;
    if (!permission.granted && permission.canAskAgain) {
      requestPermission();
    }
  }, [permission, requestPermission]);

  const handleBarcodeScanned = useCallback(
    ({ data }: { data: string }) => {
      if (alreadyDetected.current) return;
      alreadyDetected.current = true;
      onQrDetected(data);
    },
    [onQrDetected],
  );

  return (
    <View style={styles.container}>
      <View style={styles.cameraArea}>
        {permission?.granted ? (
          <CameraView
            style={StyleSheet.absoluteFill}
            facing="back"
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
