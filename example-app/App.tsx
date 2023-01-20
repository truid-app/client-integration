/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * Generated with the TypeScript template
 * https://github.com/react-native-community/react-native-template-typescript
 *
 * @format
 */
import {TRUID_EXAMPLE_DOMAIN} from '@env';
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
  type DeepLinkResult =
    | {
        success: true;
      }
    | {
        success: false;
        errorReason: string;
      }
    | undefined;

  const [result, setResult] = React.useState<DeepLinkResult>(undefined);

  React.useEffect(() => {
    Linking.addEventListener('url', event => {
      setResult(handleDeepLink(event.url));
    });

    async function getDeepLink() {
      let url = await Linking.getInitialURL();
      setResult(handleDeepLink(url));
    }

    getDeepLink();
  }, []);

  const handleDeepLink = (url: string | null): DeepLinkResult => {
    if (!url) {
      return;
    }

    let deeplinkUrl = new URL(url, true);
    let error = deeplinkUrl.query.error;
    if (error) {
      return {success: false, errorReason: error!};
    }

    return {success: true};
  };

  const isDarkMode = useColorScheme() === 'dark';

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
  };

  const confirmSignup = React.useCallback(() => {
    Linking.openURL(`${TRUID_EXAMPLE_DOMAIN}/truid/v1/confirm-signup`);
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
        {result && (
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
