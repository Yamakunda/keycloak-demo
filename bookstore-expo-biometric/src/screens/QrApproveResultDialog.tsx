import React from 'react';
import { ActivityIndicator, Button, Modal, StyleSheet, Text, View } from 'react-native';
import type { QrApproveResult } from '../state/types';

export function QrApproveResultDialog({
  result,
  onDismiss,
}: {
  result: QrApproveResult;
  onDismiss: () => void;
}) {
  return (
    <Modal transparent animationType="fade" onRequestClose={result.kind === 'approving' ? undefined : onDismiss}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          {result.kind === 'approving' && (
            <>
              <Text style={styles.title}>Đang xác nhận…</Text>
              <View style={styles.row}>
                <ActivityIndicator />
                <Text style={styles.rowText}>Đang đăng nhập giúp thiết bị kia…</Text>
              </View>
            </>
          )}
          {result.kind === 'success' && (
            <>
              <Text style={styles.title}>Thành công</Text>
              <Text style={styles.message}>Đã đăng nhập giúp thiết bị kia. Kiểm tra lại trên trình duyệt.</Text>
              <View style={styles.actions}>
                <Button title="OK" onPress={onDismiss} />
              </View>
            </>
          )}
          {result.kind === 'failed' && (
            <>
              <Text style={styles.title}>Thất bại</Text>
              <Text style={styles.message}>{result.message}</Text>
              <View style={styles.actions}>
                <Button title="Đóng" onPress={onDismiss} />
              </View>
            </>
          )}
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
  row: { flexDirection: 'row', alignItems: 'center' },
  rowText: { marginLeft: 12 },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 16 },
});
