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
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';

import {Colors} from 'react-native/Libraries/NewAppScreen';
import URL from 'url-parse';
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
  const [result, setResult] = React.useState<{
    success: boolean | undefined;
    errorReason: string | undefined;
  }>({success: undefined, errorReason: undefined});

  React.useEffect(() => {
    Linking.addEventListener('url', event => {
      setResult(handleDeepLink(event.url));
    });

    async function getDeepLink() {
      let url = await Linking.getInitialURL();
      let res = handleDeepLink(url);
      setResult(res);
    }

    getDeepLink();
  }, []);

  const handleDeepLink = (
    url: string | null,
  ): {success: boolean | undefined; errorReason: string | undefined} => {
    if (!url) {
      return {success: undefined, errorReason: undefined};
    }

    let deeplinkUrl = new URL(url, true);
    let error = deeplinkUrl.query.error;
    if (error) {
      return {success: false, errorReason: error!};
    }

    return {success: true, errorReason: undefined};
  };

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
        {result.success !== undefined && (
          <View>
            <Text>
              {result.success ? 'SUCCESS' : `FAILURE: ${result.errorReason}`}
            </Text>
          </View>
        )}
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
