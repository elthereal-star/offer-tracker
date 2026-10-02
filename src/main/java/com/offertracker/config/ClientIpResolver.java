package com.offertracker.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<Network> trustedProxies;

    public ClientIpResolver(TrustedProxyProperties properties) {
        this.trustedProxies = properties.trustedProxyCidrs().stream().map(Network::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null || !trustedProxies.stream().anyMatch(network -> network.contains(remote))) return remoteOrUnknown(remote);
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return remoteOrUnknown(remote);
        String[] addresses = forwarded.split(",");
        for (int i = addresses.length - 1; i >= 0; i--) {
            String candidate = normalize(addresses[i].trim());
            if (candidate != null && !trustedProxies.stream().anyMatch(network -> network.contains(candidate))) return candidate;
        }
        return remoteOrUnknown(remote);
    }

    private String remoteOrUnknown(String remote) { return remote == null ? "unknown" : remote; }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }

    private record Network(byte[] address, int prefix) {
        static Network parse(String value) {
            String[] parts = value.trim().split("/", 2);
            try {
                InetAddress address = InetAddress.getByName(parts[0]);
                int max = address.getAddress().length * 8;
                int prefix = parts.length == 1 ? max : Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > max) throw new IllegalArgumentException();
                return new Network(address.getAddress(), prefix);
            } catch (Exception ex) { throw new IllegalArgumentException("无效的可信代理网段: " + value, ex); }
        }

        boolean contains(String value) {
            try {
                byte[] candidate = InetAddress.getByName(value).getAddress();
                if (candidate.length != address.length) return false;
                BigInteger mask = prefix == 0 ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(address.length * 8).subtract(BigInteger.ONE).shiftRight(address.length * 8 - prefix).shiftLeft(address.length * 8 - prefix);
                return new BigInteger(1, candidate).and(mask).equals(new BigInteger(1, address).and(mask));
            } catch (Exception ignored) { return false; }
        }
    }
}
