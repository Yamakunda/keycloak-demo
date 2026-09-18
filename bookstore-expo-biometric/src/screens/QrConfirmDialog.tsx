import React from 'react';
import { Modal, StyleSheet, Text, View, Button } from 'react-native';

export function QrConfirmDialog({
  onConfirm,
  onDismiss,
}: {
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  return (
    <Modal transparent animationType="fade" onRequestClose={onDismiss}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Text style={styles.title}>Cho phép đăng nhập?</Text>
          <Text style={styles.message}>
            Đã quét mã QR trên thiết bị khác. Xác thực vân tay để cho phép thiết bị đó đăng
            nhập vào tài khoản của bạn.
          </Text>
          <View style={styles.actions}>
            <Button title="Từ chối" onPress={onDismiss} />
            <View style={styles.actionSpacer} />
            <Button title="Xác nhận" onPress={onConfirm} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 20, width: '100%' },
  title: { fontSize: 17, fontWeight: '600', marginBottom: 8 },
  message: { fontSize: 14, color: '#333' },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 16 },
  actionSpacer: { width: 12 },
});
