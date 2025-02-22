package com.netease.nim.camellia.redis.proxy.tls.frontend;

import com.netease.nim.camellia.redis.proxy.conf.ProxyDynamicConf;
import com.netease.nim.camellia.tools.ssl.SSLContextUtil;
import com.netease.nim.camellia.tools.utils.FileUtils;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * Created by caojiajun on 2023/8/9
 */
public class DefaultProxyFrontendTlsProvider implements ProxyFrontendTlsProvider {

    private SSLContext sslContext;
    @Override
    public boolean init() {
        createSSLContext();
        return true;
    }

    @Override
    public SslHandler createSslHandler() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        String needClientAuth = ProxyDynamicConf.getString("proxy.frontend.tls.need.client.auth", "true");
        if (needClientAuth != null) {
            if (needClientAuth.equalsIgnoreCase("true")) {
                sslEngine.setNeedClientAuth(true);
            } else if (needClientAuth.equalsIgnoreCase("false")) {
                sslEngine.setNeedClientAuth(false);
            }
        }
        String wantClientAuth = ProxyDynamicConf.getString("proxy.frontend.tls.want.client.auth", "");
        if (wantClientAuth != null) {
            if (wantClientAuth.equalsIgnoreCase("true")) {
                sslEngine.setWantClientAuth(true);
            } else if (wantClientAuth.equalsIgnoreCase("false")) {
                sslEngine.setWantClientAuth(false);
            }
        }
        String enableSessionCreation = ProxyDynamicConf.getString("proxy.frontend.tls.enable.session.creation", "");
        if (enableSessionCreation != null) {
            if (enableSessionCreation.equalsIgnoreCase("true")) {
                sslEngine.setEnableSessionCreation(true);
            } else if (enableSessionCreation.equalsIgnoreCase("false")) {
                sslEngine.setEnableSessionCreation(false);
            }
        }
        String enabledProtocols = ProxyDynamicConf.getString("proxy.frontend.tls.enable.protocols", "");
        if (enabledProtocols != null && enabledProtocols.trim().length() > 0) {
            String[] protocols = enabledProtocols.split(",");
            sslEngine.setEnabledProtocols(protocols);
        }
        String enabledCipherSuites = ProxyDynamicConf.getString("proxy.frontend.tls.enable.cipher.suites", "");
        if (enabledCipherSuites != null && enabledCipherSuites.trim().length() > 0) {
            String[] cipherSuites = enabledCipherSuites.split(",");
            sslEngine.setEnabledCipherSuites(cipherSuites);
        }
        sslEngine.setUseClientMode(false);
        return new SslHandler(sslEngine);
    }

    private void createSSLContext() {
        String caCertFilePath;
        String caCertFile = ProxyDynamicConf.getString("proxy.frontend.tls.ca.cert.file", null);
        if (caCertFile == null) {
            caCertFilePath = ProxyDynamicConf.getString("proxy.frontend.tls.ca.cert.file.path", null);
        } else {
            caCertFilePath = FileUtils.getClasspathFilePath(caCertFile);
        }
        String crtFilePath;
        String crtFile = ProxyDynamicConf.getString("proxy.frontend.tls.cert.file", null);
        if (crtFile == null) {
            crtFilePath = ProxyDynamicConf.getString("proxy.frontend.tls.cert.file.path", null);
        } else {
            crtFilePath = FileUtils.getClasspathFilePath(crtFile);
        }
        if (crtFilePath == null) {
            throw new IllegalArgumentException("crtFilePath not found");
        }
        String keyFilePath;
        String keyFile = ProxyDynamicConf.getString("proxy.frontend.tls.key.file", null);
        if (keyFile == null) {
            keyFilePath = ProxyDynamicConf.getString("proxy.frontend.tls.key.file.path", null);
        } else {
            keyFilePath = FileUtils.getClasspathFilePath(keyFile);
        }
        if (keyFilePath == null) {
            throw new IllegalArgumentException("keyFilePath not found");
        }
        String password = ProxyDynamicConf.getString("proxy.frontend.tls.password", null);
        this.sslContext = SSLContextUtil.genSSLContext(caCertFilePath, crtFilePath, keyFilePath, password);
    }


}
