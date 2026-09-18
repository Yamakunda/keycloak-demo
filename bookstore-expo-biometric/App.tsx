import { StatusBar } from 'expo-status-bar';
import React from 'react';
import { StyleSheet, View } from 'react-native';
import { BooksScreen } from './src/screens/BooksScreen';
import { LoadingScreen } from './src/screens/LoadingScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { AppProvider, useApp } from './src/state/AppContext';

function Root() {
  const { state } = useApp();

  switch (state.kind) {
    case 'loggedOut':
      return <LoginScreen savedUsername={state.savedUsername} />;
    case 'loggingIn':
      return <LoadingScreen message="Đang mở trang đăng nhập…" />;
    case 'loggedIn':
      return <BooksScreen username={state.username} error={state.error} />;
  }
}

export default function App() {
  return (
    <AppProvider>
      <View style={styles.container}>
        <Root />
        <StatusBar style="auto" />
      </View>
    </AppProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
});
