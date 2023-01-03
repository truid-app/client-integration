/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * Generated with the TypeScript template
 * https://github.com/react-native-community/react-native-template-typescript
 *
 * @format
 */

import React from 'react';
import {
  Button,
  ImageBackground,
  Linking,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';

import {Colors} from 'react-native/Libraries/NewAppScreen';

const Header = () => {
  return (
    <ImageBackground
      accessibilityRole="image"
      source={require('react-native/Libraries/NewAppScreen/components/logo.png')}
      style={styles.background}
      imageStyle={styles.logo}>
      <Text style={styles.text}>
        Welcome to
        {'\n'}
        Truid Example App
      </Text>
    </ImageBackground>
  );
};

const App = () => {
  const isDarkMode = useColorScheme() === 'dark';

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
  };

  const confirmSignup = React.useCallback(() => {
    Linking.openURL('http://localhost:8080/truid/v1/confirm-signup');
  }, []);

  return (
    <SafeAreaView style={backgroundStyle}>
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}>
        <Header />
        <View style={backgroundStyle}>
          <Button title="Log in" disabled={true} />
        </View>
        <View style={backgroundStyle}>
          <Button title="Sign up" onPress={confirmSignup} />
        </View>
        <View style={backgroundStyle}>
          <Button title="Perform action" disabled={true} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  background: {
    paddingBottom: 40,
    paddingTop: 96,
    paddingHorizontal: 32,
  },
  logo: {
    opacity: 0.2,
    overflow: 'visible',
    resizeMode: 'cover',
    marginLeft: -128,
    marginBottom: -192,
  },
  text: {
    color: Colors.black,
    fontSize: 40,
    fontWeight: '700',
    textAlign: 'center',
  },
});

export default App;
