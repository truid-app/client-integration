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
    result: boolean | null;
    errorReason: string | null;
  }>({result: null, errorReason: null});

  React.useEffect(() => {
    let linking = new DeepLinking();
    linking.addEventListener('url', async event => {
      setResult(parseUrl(event.url));
    });
    async function getDeepLink() {
      const url = await linking.getInitialUrl();
      let res = parseUrl(url);
      setResult(res);
    }
    getDeepLink();
  }, []);

  const parseUrl = (
    url: string | undefined,
  ): {result: boolean | null; errorReason: string | null} => {
    if (!url) {
      return {result: null, errorReason: null};
    } else if (url.indexOf('error') > -1) {
      let match = url.match('(?<=error\\=)(.*?)(?=\\&)');
      let errorReason: string | null = null;
      if (match && match.length > 0) {
        errorReason = match[0];
      }
      return {result: false, errorReason: errorReason};
    } else {
      return {result: true, errorReason: null};
    }
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
        {result.result != null && (
          <View>
            <Text>
              {result.result ? 'SUCCESS' : `FAILURE: ${result.errorReason}`}
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

class DeepLinking {
  async getInitialUrl(): Promise<string | undefined> {
    const initialUrl = await Linking.getInitialURL();
    return initialUrl ?? undefined;
  }

  addEventListener(type: 'url', handler: (event: {url: string}) => void): void {
    Linking.addEventListener(type, event => {
      handler(event);
    });
  }
}

export default App;
