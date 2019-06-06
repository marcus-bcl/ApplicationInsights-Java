package datadog.trace.instrumentation.jdbc;

import static datadog.trace.instrumentation.jdbc.JDBCMaps.DBInfo.DEFAULT;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Structured as an enum instead of a class hierarchy to allow iterating through the parsers, plus
 * the added benefit of only a single class to inject.
 */
public enum JDBCConnectionUrlParser {
  GENERIC_URL_LIKE() {
    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      try {
        // Attempt generic parsing
        final URI uri = new URI(jdbcUrl);

        String username = uri.getUserInfo();
        String databaseName = null;
        if (uri.getQuery() != null) {
          final Map<String, String> queryParams = splitQuery(uri.getQuery(), "&");

          if (username == null) {
            username = queryParams.get("user");
          }
          databaseName = queryParams.get("databasename");
        }

        String path = uri.getPath();
        if (path.startsWith("/")) {
          path = path.substring(1);
        }

        return new JDBCMaps.DBInfo(
            uri.getScheme(), null, username, path, databaseName, uri.getHost(), uri.getPort());
      } catch (final Exception e) {
        return DEFAULT;
      }
    }
  },

  MODIFIED_URL_LIKE() {
    private static final String DEFAULT_HOST = "localhost";

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      try {

        final String type;
        String serverName = "";
        Integer port = null;
        String databaseName = null;
        String instanceName = null;
        String username = null;

        final int hostIndex = jdbcUrl.indexOf("://");

        if (hostIndex <= 0) {
          return DEFAULT;
        }

        type = jdbcUrl.substring(0, hostIndex);

        final String[] split;
        if (type.equals("db2") || type.equals("as400")) {
          if (jdbcUrl.contains("=")) {
            final int paramLoc = jdbcUrl.lastIndexOf(":");
            split = new String[] {jdbcUrl.substring(0, paramLoc), jdbcUrl.substring(paramLoc + 1)};
          } else {
            split = new String[] {jdbcUrl};
          }
        } else {
          split = jdbcUrl.split(";", 2);
        }

        if (split.length > 1) {
          final Map<String, String> urlProps = splitQuery(split[1], ";");
          if (urlProps.containsKey("servername")) {
            serverName = urlProps.get("servername");
          }
          if (urlProps.containsKey("instancename")) {
            instanceName = urlProps.get("instancename");
          }
          if (urlProps.containsKey("databasename")) {
            databaseName = urlProps.get("databasename");
          }
          if (urlProps.containsKey("user")) {
            username = urlProps.get("user");
          }
        }

        final String urlServerName = split[0].substring(hostIndex + 3);
        if (!urlServerName.isEmpty()) {
          serverName = urlServerName;
        }

        int instanceLoc = serverName.indexOf("/");
        if (instanceLoc > 1) {
          instanceName = serverName.substring(instanceLoc + 1);
          serverName = serverName.substring(0, instanceLoc);
        }

        final int portLoc = serverName.indexOf(":");

        if (portLoc > 1) {
          port = Integer.parseInt(serverName.substring(portLoc + 1));
          serverName = serverName.substring(0, portLoc);
        }

        instanceLoc = serverName.indexOf("\\");
        if (instanceLoc > 1) {
          instanceName = serverName.substring(instanceLoc + 1);
          serverName = serverName.substring(0, instanceLoc);
        }

        if (serverName.isEmpty()) {
          serverName = DEFAULT_HOST;
        }

        return new JDBCMaps.DBInfo(
            type, null, username, instanceName, null /* databaseName */, serverName, port);
      } catch (final UnsupportedEncodingException e) {
        return DEFAULT;
      }
    }
  },

  POSTGRES("postgresql") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      final JDBCMaps.DBInfo dbInfo = GENERIC_URL_LIKE.doParse(jdbcUrl, props);
      final String host = dbInfo.getHost() == null ? DEFAULT_HOST : dbInfo.getHost();
      final int port = dbInfo.getPort() <= 0 ? DEFAULT_PORT : dbInfo.getPort();
      return new JDBCMaps.DBInfo(
          dbInfo.getType(),
          dbInfo.getUrl(),
          dbInfo.getUser(),
          dbInfo.getInstance(),
          null,
          host,
          port);
    }
  },

  MYSQL("mysql", "mariadb") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3306;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      final JDBCMaps.DBInfo dbInfo = GENERIC_URL_LIKE.doParse(jdbcUrl, props);
      final String host = dbInfo.getHost() == null ? DEFAULT_HOST : dbInfo.getHost();
      final int port = dbInfo.getPort() <= 0 ? DEFAULT_PORT : dbInfo.getPort();
      return new JDBCMaps.DBInfo(
          dbInfo.getType(),
          dbInfo.getUrl(),
          dbInfo.getUser(),
          dbInfo.getInstance(),
          null,
          host,
          port);
    }
  },

  SAP("sap") {
    private static final String DEFAULT_HOST = "localhost";

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      final JDBCMaps.DBInfo dbInfo = GENERIC_URL_LIKE.doParse(jdbcUrl, props);
      final String host = dbInfo.getHost() == null ? DEFAULT_HOST : dbInfo.getHost();
      return new JDBCMaps.DBInfo(
          dbInfo.getType(),
          dbInfo.getUrl(),
          dbInfo.getUser(),
          dbInfo.getDb(),
          null,
          host,
          dbInfo.getPort());
    }
  },

  MSSQLSERVER("microsoft", "sqlserver") {
    private static final int DEFAULT_PORT = 1433;
    private static final String DEFAULT_INSTANCE = "MSSQLSERVER";

    @Override
    JDBCMaps.DBInfo doParse(String jdbcUrl, final Properties props) {
      if (jdbcUrl.startsWith("microsoft:")) {
        jdbcUrl = jdbcUrl.substring("microsoft:".length());
      }
      if (!jdbcUrl.startsWith("sqlserver://")) {
        return DEFAULT;
      }

      final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

      return new JDBCMaps.DBInfo(
          "sqlserver",
          dbInfo.getUrl(),
          dbInfo.getUser(),
          dbInfo.getInstance() == null ? DEFAULT_INSTANCE : dbInfo.getInstance(),
          dbInfo.getDb(),
          dbInfo.getHost(),
          dbInfo.getPort() == null ? DEFAULT_PORT : dbInfo.getPort());
    }
  },

  DB2("db2", "as400") {
    private static final int DEFAULT_PORT = 50000;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);
      return dbInfo.getPort() != null
          ? dbInfo
          : new JDBCMaps.DBInfo(
              dbInfo.getType(),
              dbInfo.getUrl(),
              dbInfo.getUser(),
              dbInfo.getInstance(),
              dbInfo.getDb(),
              dbInfo.getHost(),
              DEFAULT_PORT);
    }
  },

  H2("h2") {
    private static final int DEFAULT_PORT = 8082;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      String type = "h2";
      String instance = null;

      final String h2Url = jdbcUrl.substring("h2:".length());
      if (h2Url.startsWith("mem:")) {
        type = "h2:mem";
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("mem:".length(), propLoc);
        } else {
          instance = h2Url.substring("mem:".length());
        }
      } else if (h2Url.startsWith("file:")) {
        type = "h2:file";
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("file:".length(), propLoc);
        } else {
          instance = h2Url.substring("file:".length());
        }
      } else if (h2Url.startsWith("zip:")) {
        type = "h2:zip";
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("zip:".length(), propLoc);
        } else {
          instance = h2Url.substring("zip:".length());
        }
      } else if (h2Url.startsWith("tcp:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "h2:tcp",
            dbInfo.getUrl(),
            dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? DEFAULT_PORT : dbInfo.getPort());
      } else if (h2Url.startsWith("ssl:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "h2:ssl",
            dbInfo.getUrl(),
            dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? DEFAULT_PORT : dbInfo.getPort());
      } else {
        type = "h2:file";
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring(0, propLoc);
        } else {
          instance = h2Url;
        }
      }
      return new JDBCMaps.DBInfo(type, null, null, instance, null, null, null);
    }
  },

  HSQL("hsqldb") {
    private static final String DEFAULT_USER = "SA";
    private static final int DEFAULT_PORT = 9001;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      String type = "hsqldb";
      String instance = null;
      final String hsqlUrl = jdbcUrl.substring("hsqldb:".length());
      if (hsqlUrl.startsWith("mem:")) {
        type = "hsqldb:mem";
        instance = hsqlUrl.substring("mem:".length());
      } else if (hsqlUrl.startsWith("file:")) {
        type = "hsqldb:file";
        instance = hsqlUrl.substring("file:".length());
      } else if (hsqlUrl.startsWith("res:")) {
        type = "hsqldb:res";
        instance = hsqlUrl.substring("res:".length());
      } else if (hsqlUrl.startsWith("hsql:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "hsqldb:hsql",
            dbInfo.getUrl(),
            dbInfo.getUser() == null ? DEFAULT_USER : dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? DEFAULT_PORT : dbInfo.getPort());

      } else if (hsqlUrl.startsWith("hsqls:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "hsqldb:hsqls",
            dbInfo.getUrl(),
            dbInfo.getUser() == null ? DEFAULT_USER : dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? DEFAULT_PORT : dbInfo.getPort());

      } else if (hsqlUrl.startsWith("http:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "hsqldb:http",
            dbInfo.getUrl(),
            dbInfo.getUser() == null ? DEFAULT_USER : dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? 80 : dbInfo.getPort());

      } else if (hsqlUrl.startsWith("https:")) {
        final JDBCMaps.DBInfo dbInfo = MODIFIED_URL_LIKE.doParse(jdbcUrl, props);

        return new JDBCMaps.DBInfo(
            "hsqldb:https",
            dbInfo.getUrl(),
            dbInfo.getUser() == null ? DEFAULT_USER : dbInfo.getUser(),
            dbInfo.getInstance(),
            dbInfo.getDb(),
            dbInfo.getHost(),
            dbInfo.getPort() == null ? 443 : dbInfo.getPort());

      } else {
        type = "hsqldb:mem";
        instance = hsqlUrl;
      }
      return new JDBCMaps.DBInfo(type, null, DEFAULT_USER, instance, null, null, null);
    }
  },

  DERBY("derby") {
    private static final String DEFAULT_USER = "APP";
    private static final int DEFAULT_PORT = 1527;

    @Override
    JDBCMaps.DBInfo doParse(final String jdbcUrl, final Properties props) {
      String type = "derby";
      String instance = null;
      String host = null;
      Integer port = null;
      String user = DEFAULT_USER;

      if (props != null) {
        instance = props.getProperty("databasename");
        user = props.getProperty("user");
      }
      final String derbyUrl = jdbcUrl.substring("derby:".length());
      final String[] split = derbyUrl.split(";", 2);

      if (split.length > 1) {
        try {
          final Map<String, String> urlProps = splitQuery(split[1], ";");
          if (urlProps.containsKey("databasename")) {
            instance = urlProps.get("databasename");
          }
          if (urlProps.containsKey("user")) {
            user = urlProps.get("user");
          }
          if (urlProps.containsKey("servername")) {
            host = urlProps.get("servername");
          }
          if (urlProps.containsKey("portnumber")) {
            port = Integer.parseInt(urlProps.get("portnumber"));
          }
        } catch (final Exception e) {
        }
      }

      if (split[0].startsWith("memory:")) {
        type = "derby:memory";
        final String urlInstance = split[0].substring("memory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (split[0].startsWith("directory:")) {
        type = "derby:directory";
        final String urlInstance = split[0].substring("directory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (split[0].startsWith("classpath:")) {
        type = "derby:classpath";
        final String urlInstance = split[0].substring("classpath:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (split[0].startsWith("jar:")) {
        type = "derby:jar";
        final String urlInstance = split[0].substring("jar:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (split[0].startsWith("//")) {
        type = "derby:network";
        String url = split[0].substring("//".length());
        final int instanceLoc = url.indexOf("/");
        if (instanceLoc >= 0) {
          instance = url.substring(instanceLoc + 1);
          final int protoLoc = instance.indexOf(":");
          if (protoLoc >= 0) {
            instance = instance.substring(protoLoc + 1);
          }
          url = url.substring(0, instanceLoc);
        }
        final int portLoc = url.indexOf(":");
        if (portLoc > 0) {
          host = url.substring(0, portLoc);
          port = Integer.parseInt(url.substring(portLoc + 1));
        } else {
          host = url;
          port = DEFAULT_PORT;
        }
      } else {
        type = "derby:directory";
        final String urlInstance = split[0];
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      }

      return new JDBCMaps.DBInfo(type, null, user, instance, null, host, port);
    }
  };

  private static final Map<String, JDBCConnectionUrlParser> typeParsers = new HashMap<>();

  static {
    for (final JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      for (final String key : parser.typeKeys) {
        typeParsers.put(key, parser);
      }
    }
  }

  private final String[] typeKeys;

  JDBCConnectionUrlParser(final String... typeKeys) {
    this.typeKeys = typeKeys;
  }

  abstract JDBCMaps.DBInfo doParse(String jdbcUrl, final Properties props);

  public static JDBCMaps.DBInfo parse(String connectionUrl, final Properties props) {
    if (connectionUrl == null) {
      return DEFAULT;
    }
    // Make this easer and ignore case.
    connectionUrl = connectionUrl.toLowerCase();

    if (!connectionUrl.startsWith("jdbc:")) {
      return DEFAULT;
    }

    final String jdbcUrl = connectionUrl.substring("jdbc:".length());
    final int typeLoc = jdbcUrl.indexOf(':');

    if (typeLoc < 1) {
      // Invalid format: `jdbc:` or `jdbc::`
      return DEFAULT;
    }

    final String baseType = jdbcUrl.substring(0, typeLoc);

    if (typeParsers.containsKey(baseType)) {
      // Delegate to specific parser
      return typeParsers.get(baseType).doParse(jdbcUrl, props);
    }
    return GENERIC_URL_LIKE.doParse(connectionUrl, props);
  }

  // Source: https://stackoverflow.com/a/13592567
  private static Map<String, String> splitQuery(final String query, final String separator)
      throws UnsupportedEncodingException {
    final Map<String, String> query_pairs = new LinkedHashMap<>();
    final String[] pairs = query.split(separator);
    for (final String pair : pairs) {
      final int idx = pair.indexOf("=");
      final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
      if (!query_pairs.containsKey(key)) {
        final String value =
            idx > 0 && pair.length() > idx + 1
                ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                : null;
        query_pairs.put(key, value);
      }
    }
    return query_pairs;
  }
}
