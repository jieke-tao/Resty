package cn.dreampie.orm.provider.druid;

import cn.dreampie.common.util.properties.Prop;
import cn.dreampie.common.util.properties.Proper;
import cn.dreampie.orm.DataSourceProvider;
import cn.dreampie.orm.dialect.Dialect;
import cn.dreampie.orm.dialect.DialectFactory;
import com.alibaba.druid.DruidRuntimeException;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static cn.dreampie.common.util.Checker.checkNotNull;


/**
 * Created by ice on 14-12-30.
 */
public class DruidDataSourceProvider implements DataSourceProvider {

  private String dsName;
  // 基本属性 url、user、password
  private String url;
  private String user;
  private String password;
  private String driverClass;  // 由 "com.mysql.jdbc.Driver" 改为 null 让 druid 自动探测 driverClass 值

  // 初始连接池大小、最小空闲连接数、最大活跃连接数
  private int initialSize = 10;
  private int minIdle = 10;
  private int maxActive = 100;

  // 配置获取连接等待超时的时间
  private long maxWait = DruidDataSource.DEFAULT_MAX_WAIT;

  // 配置间隔多久才进行一次检测，检测需要关闭的空闲连接，单位是毫秒
  private long timeBetweenEvictionRunsMillis = DruidDataSource.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
  // 配置连接在池中最小生存的时间
  private long minEvictableIdleTimeMillis = DruidDataSource.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
  // 配置发生错误时多久重连
  private long timeBetweenConnectErrorMillis = DruidDataSource.DEFAULT_TIME_BETWEEN_CONNECT_ERROR_MILLIS;

  /**
   * hsqldb - "select 1 from INFORMATION_SCHEMA.SYSTEM_USERS"
   * Oracle - "select 1 from dual"
   * DB2 - "select 1 from sysibm.sysdummy1"
   * mysql - "select 1"
   */
  private String validationQuery = "select 1";
  private boolean testWhileIdle = true;
  private boolean testOnBorrow = false;
  private boolean testOnReturn = false;

  // 是否打开连接泄露自动检测
  private boolean removeAbandoned = false;
  // 连接长时间没有使用，被认为发生泄露时长
  private long removeAbandonedTimeoutMillis = 300 * 1000;
  // 发生泄露时是否需要输出 log，建议在开启连接泄露检测时开启，方便排错
  private boolean logAbandoned = false;

  // 是否缓存preparedStatement，即PSCache，对支持游标的数据库性能提升巨大，如 oracle、mysql 5.5 及以上版本
  // private boolean poolPreparedStatements = false;	// oracle、mysql 5.5 及以上版本建议为 true;

  // 只要maxPoolPreparedStatementPerConnectionSize>0,poolPreparedStatements就会被自动设定为true，使用oracle时可以设定此值。
  private int maxPoolPreparedStatementPerConnectionSize = -1;

  // 配置监控统计拦截的filters
  private String filters;  // 监控统计："stat"    防SQL注入："wall"     组合使用： "stat,wall"
  private List<Filter> filterList;

  private DruidDataSource ds;
  private Dialect dialect;

  public DruidDataSourceProvider() {
    this("default");
  }

  public DruidDataSourceProvider(String dsName) {
    this.dsName = dsName;
    Prop prop = Proper.use("application.properties");
    this.url = prop.get("db." + dsName + ".url");
    checkNotNull(this.url, "Could not found database url for " + "db." + dsName + ".url");
    this.user = prop.get("db." + dsName + ".user");
    checkNotNull(this.user, "Could not found database user for " + "db." + dsName + ".user");
    this.password = prop.get("db." + dsName + ".password");
    checkNotNull(this.password, "Could not found database password for " + "db." + dsName + ".password");
    this.dialect = DialectFactory.get(prop.get("db." + dsName + ".dialect", "mysql"));
    this.driverClass = prop.get("db." + dsName + ".driver");
    this.filters = prop.get("db." + dsName + ".filter");
    this.initialSize = prop.getInt("db." + dsName + ".initialSize", 10);
    this.minIdle = prop.getInt("db." + dsName + ".minIdle", 10);
    this.maxActive = prop.getInt("db." + dsName + ".maxActive", 100);
    this.maxWait = prop.getInt("db." + dsName + ".maxWait", DruidDataSource.DEFAULT_MAX_WAIT);
    this.timeBetweenEvictionRunsMillis = prop.getLong("db." + dsName + ".timeBetweenEvictionRunsMillis", DruidDataSource.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS);
    this.minEvictableIdleTimeMillis = prop.getLong("db." + dsName + ".minEvictableIdleTimeMillis", DruidDataSource.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    this.timeBetweenConnectErrorMillis = prop.getLong("db." + dsName + ".timeBetweenConnectErrorMillis", DruidDataSource.DEFAULT_TIME_BETWEEN_CONNECT_ERROR_MILLIS);
    this.validationQuery = this.dialect.validQuery();
    this.testWhileIdle = prop.getBoolean("db." + dsName + ".testWhileIdle", true);
    this.testOnBorrow = prop.getBoolean("db." + dsName + ".testOnBorrow", false);
    this.testOnReturn = prop.getBoolean("db." + dsName + ".testOnReturn", false);
    this.removeAbandoned = prop.getBoolean("db." + dsName + ".removeAbandoned", false);
    this.removeAbandonedTimeoutMillis = prop.getInt("db." + dsName + ".removeAbandonedTimeoutMillis", 300 * 1000);
    this.logAbandoned = prop.getBoolean("db." + dsName + ".logAbandoned", false);
    this.maxPoolPreparedStatementPerConnectionSize = prop.getInt("db." + dsName + ".maxPoolPreparedStatementPerConnectionSize",10);

    //init druid
    ds = new DruidDataSource();
    ds.setUrl(url);
    ds.setUsername(user);
    ds.setPassword(password);
    if (driverClass != null)
      ds.setDriverClassName(driverClass);
    ds.setInitialSize(initialSize);
    ds.setMinIdle(minIdle);
    ds.setMaxActive(maxActive);
    ds.setMaxWait(maxWait);
    ds.setTimeBetweenConnectErrorMillis(timeBetweenConnectErrorMillis);
    ds.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    ds.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);

    ds.setValidationQuery(validationQuery);
    ds.setTestWhileIdle(testWhileIdle);
    ds.setTestOnBorrow(testOnBorrow);
    ds.setTestOnReturn(testOnReturn);

    ds.setRemoveAbandoned(removeAbandoned);
    ds.setRemoveAbandonedTimeoutMillis(removeAbandonedTimeoutMillis);
    ds.setLogAbandoned(logAbandoned);

    //只要maxPoolPreparedStatementPerConnectionSize>0,poolPreparedStatements就会被自动设定为true，参照druid的源码
    ds.setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);

    if (filters != null)
      try {
        ds.setFilters(filters);
      } catch (SQLException e) {
        throw new DruidRuntimeException(e.getMessage(), e);
      }

    addFilterList(ds);
  }

  /**
   * 设置过滤器，如果要开启监控统计需要使用此方法或在构造方法中进行设置
   * <p>
   * 监控统计："stat"
   * 防SQL注入："wall"
   * 组合使用： "stat,wall"
   * </p>
   */
  public DruidDataSourceProvider setFilters(String filters) {
    this.filters = filters;
    return this;
  }

  public synchronized DruidDataSourceProvider addFilter(Filter filter) {
    if (filterList == null)
      filterList = new ArrayList<Filter>();
    filterList.add(filter);
    return this;
  }

  private void addFilterList(DruidDataSource ds) {
    if (filterList != null) {
      List<Filter> targetList = ds.getProxyFilters();
      for (Filter add : filterList) {
        boolean found = false;
        for (Filter target : targetList) {
          if (add.getClass().equals(target.getClass())) {
            found = true;
            break;
          }
        }
        if (!found)
          targetList.add(add);
      }
    }
  }

  public DataSource getDataSource() {
    return ds;
  }

  public Dialect getDialect() {
    return dialect;
  }

  public String getDsName() {
    return dsName;
  }

  public DruidDataSourceProvider set(int initialSize, int minIdle, int maxActive) {
    this.initialSize = initialSize;
    this.minIdle = minIdle;
    this.maxActive = maxActive;
    return this;
  }

  public DruidDataSourceProvider setDriverClass(String driverClass) {
    this.driverClass = driverClass;
    return this;
  }

  public DruidDataSourceProvider setInitialSize(int initialSize) {
    this.initialSize = initialSize;
    return this;
  }

  public DruidDataSourceProvider setMinIdle(int minIdle) {
    this.minIdle = minIdle;
    return this;
  }

  public DruidDataSourceProvider setMaxActive(int maxActive) {
    this.maxActive = maxActive;
    return this;
  }

  public DruidDataSourceProvider setMaxWait(long maxWait) {
    this.maxWait = maxWait;
    return this;
  }

  public DruidDataSourceProvider setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
    this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    return this;
  }

  public DruidDataSourceProvider setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
    this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    return this;
  }

  /**
   * hsqldb - "select 1 from INFORMATION_SCHEMA.SYSTEM_USERS"
   * Oracle - "select 1 from dual"
   * DB2 - "select 1 from sysibm.sysdummy1"
   * mysql - "select 1"
   */
  public DruidDataSourceProvider setValidationQuery(String validationQuery) {
    this.validationQuery = validationQuery;
    return this;
  }

  public DruidDataSourceProvider setTestWhileIdle(boolean testWhileIdle) {
    this.testWhileIdle = testWhileIdle;
    return this;
  }

  public DruidDataSourceProvider setTestOnBorrow(boolean testOnBorrow) {
    this.testOnBorrow = testOnBorrow;
    return this;
  }

  public DruidDataSourceProvider setTestOnReturn(boolean testOnReturn) {
    this.testOnReturn = testOnReturn;
    return this;
  }

  public DruidDataSourceProvider setMaxPoolPreparedStatementPerConnectionSize(int maxPoolPreparedStatementPerConnectionSize) {
    this.maxPoolPreparedStatementPerConnectionSize = maxPoolPreparedStatementPerConnectionSize;
    return this;
  }

  public final void setTimeBetweenConnectErrorMillis(long timeBetweenConnectErrorMillis) {
    this.timeBetweenConnectErrorMillis = timeBetweenConnectErrorMillis;
  }

  public final void setRemoveAbandoned(boolean removeAbandoned) {
    this.removeAbandoned = removeAbandoned;
  }

  public final void setRemoveAbandonedTimeoutMillis(long removeAbandonedTimeoutMillis) {
    this.removeAbandonedTimeoutMillis = removeAbandonedTimeoutMillis;
  }

  public final void setLogAbandoned(boolean logAbandoned) {
    this.logAbandoned = logAbandoned;
  }
}
