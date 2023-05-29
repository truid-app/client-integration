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

async function resolveAuthorizeUrl(startUrl: string): Promise<string> {
  console.log(`HTTP ${startUrl}`);

  let res = await fetch(startUrl, {
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
    },
    // redirect: 'manual' does not work, this request will follow redirects
  }).catch((e: any) => {
    console.log(`HTTP exception ${startUrl} ${e.message}`);
    throw new Error(e);
  });

  console.log(`HTTP ${res.status} ${startUrl}`);
  let location = res.headers.get("location");

  console.log('location', location);

  if (res.ok && location) {
    return location;
  } else {
    const body = await getResponseBody(res);

    throw Error(body?.error || 'Failed to authorize')
  }
}

async function processCode(redirectUri: string): Promise<Response> {
  console.log(`HTTP ${redirectUri}`);

  const res = await fetch(redirectUri, {
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
    },
  }).catch((e: any) => {
    console.log(`HTTP exception ${redirectUri} ${e.message}`);
    throw new Error(
      'Failed to call backend'
    );
  });

  console.log(`HTTP ${res.status} ${redirectUri}`);

  if (!res.ok) {
    const body = await getResponseBody(res);

   throw new Error(body?.error || 'Failed to auhtorize');
  } else {
    return res;
  }
}

function getContentType(header: string | null): string | undefined {
  return header?.split(';')[0].trim();
}

async function getResponseBody(res: Response): Promise<any> {
  const contentType = getContentType(res.headers.get('content-type'));

  if (contentType === 'application/json') {
    return await res.json();
  }
  return undefined;
}

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

type DeepLinkResult =
  | {
      success: true;
    }
  | {
      success: false;
      errorReason: string;
    }
  | undefined;

const App = () => {
  const [result, setResult] = React.useState<DeepLinkResult>(undefined);

  React.useEffect(() => {
    Linking.addEventListener('url', event => {
      (async () => {
        setResult(await handleDeepLink(event.url));
      })();
    });

    async function getDeepLink() {
      let url = await Linking.getInitialURL();
      setResult(await handleDeepLink(url));
    }

    getDeepLink();
  }, []);

  const handleDeepLink = async (url: string | null): Promise<DeepLinkResult> => {
    if (!url) {
      return;
    }

    let deeplinkUrl = new URL(url, true);
    console.log('url', deeplinkUrl);

    try {
      const response = await processCode(url);
      if( response ){
        return {success: true};
      } else {
        return {success: false, errorReason: 'unknown'};
      }
    } catch(e: any){
      console.log('Failed to handle callback', e);
    }
  };

  const isDarkMode = useColorScheme() === 'dark';

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
  };

  const confirmSignup = React.useCallback(async () => {
    try {
      let authorizeUrl = await resolveAuthorizeUrl(`${TRUID_EXAMPLE_DOMAIN}/truid/v1/confirm-signup`);
      console.log(`Opening url`, authorizeUrl);
      Linking.openURL(authorizeUrl);
    } catch(e: any){
      console.log('Failed to resolve authorize url', e);
    }
  }, []);

  const loginSession = React.useCallback(async () => {
    try {
      let authorizeUrl = await resolveAuthorizeUrl(`${TRUID_EXAMPLE_DOMAIN}/truid/v1/login-session`);
      console.log(`Opening url`, authorizeUrl);
      Linking.openURL(authorizeUrl);
    } catch(e: any){
      console.log('Failed to resolve authorize url', e);
    }
  }, []);

  return (
    <SafeAreaView style={backgroundStyle}>
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}>
        <Header />
        <View style={backgroundStyle}>
          <Button title="Log in" onPress={loginSession}  />
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
