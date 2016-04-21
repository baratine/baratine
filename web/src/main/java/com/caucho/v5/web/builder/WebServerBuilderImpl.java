/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.web.builder;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Provider;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.RootBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerBartenderState;
import com.caucho.v5.cli.args.ArgsBase;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.IncludeOnClass;
import com.caucho.v5.config.yaml.YamlParser;
import com.caucho.v5.convert.ConvertStringDefault;
import com.caucho.v5.deploy2.DeploySystem2;
import com.caucho.v5.health.shutdown.ShutdownSystem;
import com.caucho.v5.health.warning.WarningSystem;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.http.container.HttpSystem;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectorAmp.InjectBuilderAmp;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.log.StreamHandler;
import com.caucho.v5.network.NetworkSystem;
import com.caucho.v5.scan.ScanManager;
import com.caucho.v5.store.temp.TempStoreSystem;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;
import com.caucho.v5.web.WebServerImpl;
import com.caucho.v5.web.cli.ArgsWeb;
import com.caucho.v5.web.server.ServerBase;
import com.caucho.v5.web.server.ServerBuilderBaratine;
import com.caucho.v5.web.webapp.HttpBaratineBuilder;

import io.baratine.config.Config;
import io.baratine.config.Include;
import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManagerType;
import io.baratine.convert.ConvertManagerType.ConvertTypeBuilder;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.IncludeInject;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.web.HttpMethod;
import io.baratine.web.IncludeWeb;
import io.baratine.web.ViewRender;
import io.baratine.web.WebServer;
import io.baratine.web.WebServerBuilder;

public class WebServerBuilderImpl implements WebServerBuilder, WebServerFactory
{
  private static final L10N L = new L10N(WebServerBuilderImpl.class);
  
  private static final Logger log
    = Logger.getLogger(WebServerBuilderImpl.class.getName());
  
  private ClassLoader _parentClassLoader;
  
  private String _name = "baratine";
  
  private Config.ConfigBuilder _configBuilder = Configs.config();
  
  private ArrayList<IncludeWebAmp> _includes = new ArrayList<>();
  
  private ArrayList<ServiceBuilderWebImpl> _services = new ArrayList<>();
  
  //private ArrayList<InjectBuilderWebImpl<?>> _bindings = new ArrayList<>();
  
  private InjectBuilderAmp _injectServer = InjectorAmp.manager();
  
  private final ArrayList<IncludeInject> _bindings = new ArrayList<>();
  //private final ArrayList<IncludeWeb> _includes = new ArrayList<>();
  
  private WebServerValidator _validator = new WebServerValidator();

  private WebServerImpl _server;

  private ServerBuilderBaratine _serverBuilder;

  private SystemManager _systemManager;

  private ArrayList<Runnable> _initList = new ArrayList<>();
  
  private boolean _isEmbedded;
  private boolean _isWatchdog;
  
  // private final StatProbeManager _statProbeManager;

  //private HeapDump _heapDump;
  private MBeanServer _mbeanServer;
  private ObjectName _hotSpotName;
  private String[]_heapDumpArgs;

  private long _shutdownWaitTime = 60000L;
  
  private boolean _isSSL;
  private int _portBartender = -1;
  private int _dynamicDataIndex = -1;
  private Path _dataDirectory;

  private ServerSocketBar _ssBartender;
  private int _serverPort;
  private ServerBartender _serverSelf;

  //
  private long _startTime = System.currentTimeMillis();

  private ArgsBase _args;

  public WebServerBuilderImpl()
  {
    _startTime = CurrentTime.currentTime();
    
    _parentClassLoader = Thread.currentThread().getContextClassLoader();
    
    _name = EnvLoader.getId(_parentClassLoader);
    
    try {
      Vfs.initJNI();
    } catch (Throwable e) {
    }
    
    Path path = Vfs.path("classpath:META-INF/baratine-default.yml");
    
    try {
      if (Files.isReadable(path)) {
        List<Config> configList = YamlParser.parse(path);
      
        _configBuilder.add(configList.get(0));
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      throw new IllegalStateException(e);
    }
    
    path = Vfs.path("classpath:/baratine.yml");
    
    try {
      if (Files.isReadable(path)) {
        List<Config> configList = YamlParser.parse(path);
      
        _configBuilder.add(configList.get(0));
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    _configBuilder.add(Configs.system());
    
    initLogs();
  }
  
  private void initLogs()
  {
    Logger logRoot = Logger.getLogger("");
    
    for (Handler handler : logRoot.getHandlers()) {
      logRoot.removeHandler(handler);
    }
    
    //PathHandler pathHandler = new PathHandler();
    //pathHandler.setPath(Vfs.path("stdout:"));
    //pathHandler.init();
    
    StreamHandler streamHandler = new StreamHandler(System.out);
    streamHandler.init();
    //pathHandler.setPath(Vfs.path("stdout:"));
    //streamHandler.init();
    
    logRoot.addHandler(streamHandler);
    
    Logger.getLogger("java.management").setLevel(Level.INFO);
  }
  
  public String name()
  {
    return _name;
  }
  
  private ClassLoader parentClassLoader()
  {
    return _parentClassLoader;
  }
  
  private WebServerValidator validator()
  {
    return _validator;
  }

  @Override
  public WebServerBuilderImpl scan(Class<?> type)
  {
    ScanManager manager = new ScanManager();
    
    manager.scan(x->onScanClass(x))
           .baseClass(type)
           .go();
           
    return this;
  }

  @Override
  public WebServerBuilder scanAutoconf()
  {
    ScanManager manager = new ScanManager();
    
    manager.scan(x->onScanClass(x))
           .classNameTest(className->className.indexOf(".autoconf.") >= 0)
           .go();
           
    return this;
  }

  @Override
  public WebServerBuilder args(String []argv)
  {
    if (argv == null) {
      argv = new String[0];
    }

    _args = createArgs(argv);
    
    //_args.commandDefault(config().get("baratine.command", "start-console"));
    
    _args.parse();
    
    _configBuilder.add(_args.config());
    
    return this;
  }
  
  protected ArgsBase args()
  {
    if (_args == null) {
      _args = createArgs(new String[0]);
      
      //_args.commandDefault(config().get("baratine.command", "start-console"));
      
      _args.parse();
    }
    
    return _args;
  }
  
  protected ArgsBase createArgs(String []argv)
  {
    return new ArgsWeb(argv);
  }
  
  private void onScanClass(Class<?> type)
  {
    try {
      for (Annotation ann : type.getAnnotations()) {
        if (ann instanceof IncludeOnClass) {
          IncludeOnClass annOnClass = (IncludeOnClass) ann;
          
          Class<?> value = annOnClass.value();
          
          if (value == null) {
            return;
          }
        }
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
      return;
    }
    
    if (IncludeWeb.class.isAssignableFrom(type)) {
      include(type);
    }
    else if (type.isAnnotationPresent(Include.class)) {
      include(type);
    }
    else if (type.isAnnotationPresent(Service.class)) {
      service(type);
    }
  }
  
  @Override
  public WebServerBuilderImpl port(int port)
  {
    _configBuilder.add("server.port", port);
    
    return this;
  }
  
  @Override
  public SslBuilder ssl()
  {
    _configBuilder.add("server.ssl", true);
    
    return new SslBuilderImpl();
  }

  public Config config()
  {
    _configBuilder.converter(converter());

    Config config = _configBuilder.get();
    
    return config;
  }

  /*
  public Config config()
  {
    return _configBuilder;
  }
  */
  
  private ConvertManagerType<String> converter()
  {
    ConvertTypeBuilder<String> builder = ConvertStringDefault.build();
    
    builder.add(new ConvertStringToPath());
    
    return builder.get();
  }
  
  //
  // injection configuration
  //
  
  @Override
  public <T> BindingBuilder<T> bean(Class<T> type)
  {
    Objects.requireNonNull(type);
    

    InjectBuilderWebImpl<T> binding
      = new InjectBuilderWebImpl<>(_injectServer, type);
    
    _includes.add(binding);
    
    return binding;
  }

  @Override
  public <T> BindingBuilder<T> bean(T bean)
  {
    Objects.requireNonNull(bean);
    
    InjectBuilderWebImpl<T> binding
      = new InjectBuilderWebImpl<>(_injectServer, bean);
  
    _includes.add(binding);
  
    return binding;
  }

  @Override
  public <T> BindingBuilder<T> beanProvider(Provider<T> provider)
  {
    Objects.requireNonNull(provider);
    
    return _injectServer.provider(provider);
  }

  /*
  @Override
  public <T,X> BindingBuilder<T> beanFunction(Function<X,T> function)
  {
    Objects.requireNonNull(function);
    
    InjectBuilderWebImpl<T> binding
      = new InjectBuilderWebImpl<>(_injectServer, function);
  
    _includes.add(binding);
  
    return binding;
  }
  */

  /*
  @Override
  public <T,U> BindingBuilder<T> provider(Key<U> parent, Method m)
  {
    Objects.requireNonNull(parent);
    Objects.requireNonNull(m);
    
    return _injectServer.provider(parent, m);
  }
  */

  @Override
  public <T> ServiceRef.ServiceBuilder service(Class<T> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    validator().serviceClass(serviceClass);
    
    Key<?> key = Key.of(serviceClass, ServiceImpl.class);
    
    ServiceBuilderWebImpl service
      = new ServiceBuilderWebImpl(this, key, serviceClass);
    
    _includes.add(service);
    
    return service;
  }

  @Override
  public ServiceRef.ServiceBuilder service(Key<?> key, 
                                               Class<?> serviceClass)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(serviceClass);
    
    ServiceBuilderWebImpl service
      = new ServiceBuilderWebImpl(this, key, serviceClass);
    
    _includes.add(service);
    
    return service;
  }

  @Override
  public <T> ServiceRef.ServiceBuilder service(Class<T> serviceClass,
                                               Supplier<? extends T> supplier)
  {
    Objects.requireNonNull(supplier);
    
    ServiceBuilderWebImpl service
      = new ServiceBuilderWebImpl(this, serviceClass, supplier);
    
    _includes.add(service);
    
    return service;
  }
  
  public ArrayList<ServiceBuilderWebImpl> services()
  {
    return _services;
  }

  @Override
  public WebServerBuilder include(Class<?> includeClass)
  {
    Objects.requireNonNull(includeClass);
    
    validator().includeClass(includeClass);
    
    include(new IncludeWebClass(includeClass));

    return this;
  }
  
  @Override
  public RouteBuilderImpl route(HttpMethod method, String path)
  {
    Objects.requireNonNull(method);
    Objects.requireNonNull(path);
    
    return new RouteBuilderImpl(this, method, path);
  }
  
  @Override
  public WebSocketBuilderImpl websocket(String path)
  {
    Objects.requireNonNull(path);
    
    return new WebSocketBuilderImpl(this, path);
  }

  @Override
  public <T> WebServerBuilder view(ViewRender<T> view)
  {
    include(new ViewGen(view));

    return this;
  }

  @Override
  public <T> WebServerBuilder view(Class<? extends ViewRender<T>> viewClass)
  {
    include(new ViewClass(viewClass));

    return this;
  }

  void include(IncludeWebAmp routeGen)
  {
    _includes.add(routeGen);
  }
  
  public ArrayList<IncludeWebAmp> includes()
  {
    return _includes;
  }
  
  @Override
  public synchronized WebServer start(String ...argv)
  {
    args(argv);
    
    if (_server == null || _server.isClosed()) {
      _server = webServerFactory().build(this);
    }
    
    _server.start();
    
    return _server;
  }
  
  //@Override
  public synchronized WebServer doStart()
  {
    if (_server == null || _server.isClosed()) {
      _server = webServerFactory().build(this);
    }

    _server.start();
    
    return _server;
  }
  
  @Override
  public void go(String ...argv)
  {
    args(argv);
    
    ArgsBase args = args();
    
    args.env().put(WebServerBuilderImpl.class, this);
    
    args.doCommand();
  }
  
  private WebServerFactory webServerFactory()
  {
    try {
      for (WebServerFactory factory
            : ServiceLoader.load(WebServerFactory.class)) {
        return factory;
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    return this;
  }

  /*
  @Override
  public void join()
  {
    start();
    
    WebServerImpl server = _server;
    
    if (server != null) {
      server.join();
    }
  }
  */

  /*
  public ServerBuilderBaratine serverBuilder()
  {
    Objects.requireNonNull(_serverBuilder);
    
    return _serverBuilder;
  }
  */

  public void serverBuilder(ServerBuilderBaratine builder)
  {
    Objects.requireNonNull(builder);
    
    _serverBuilder = builder;
  }

  public void bind(IncludeInject binding)
  {
    Objects.requireNonNull(binding);
    
    _bindings.add(binding);
  }
  
  public Iterable<IncludeInject> bindings()
  {
    return _bindings;
  }

  public void service(ServiceBuilderWebImpl service)
  {
    Objects.requireNonNull(service);
    
    throw new UnsupportedOperationException();
  }

  /*
  public void include(IncludeWeb route)
  {
    Objects.requireNonNull(route);
    
    _includes.add(route);
  }
  */
  
  /*
  @Override
  protected ServerBase build(SystemManager systemManager,
                             ServerBartender serverSelf)
    throws Exception
  {
    return new ServerBaratine(this, systemManager, serverSelf);
  }
  */

  @Override
  public WebServerImpl build(WebServerBuilderImpl builder)
  {
    _serverBuilder = newServerBuilder();
    
    build();
    
    return new WebServerImpl(this);
  }
  
  protected ServerBuilderBaratine newServerBuilder()
  {
    return new ServerBuilderBaratine(config());
  }

  public SystemManager systemManager()
  {
    Objects.requireNonNull(_systemManager);
    
    return _systemManager;
  }
  
  public synchronized void stop()
  {
    WebServerImpl server = _server;
    
    _server = null;
    
    if (server != null) {
      server.close();
    }
  }

  @Override
  public InjectorAmp injector()
  {
    return _injectServer.get();
  }

  @Override
  public WebServerBuilder property(String key, String value)
  {
    _configBuilder.add(key, value);
    
    return this;
  }
  
  /*
  public ServerBuilder(String []argv)
  {
    this(new ArgsServerBase(argv).config());
  }
  */

  /*
  protected BootConfigParser createConfigParser()
  {
    return new BootConfigParser();
  }
  */
  
  /*
  protected ArgsServerBase getArgs()
  {
    return _args;
  }
  */

  public long startTime()
  {
    return _startTime;
    //return _args.getStartTime();
  }

  /*
  Socket getPingSocket()
  {
    return null;
    //return _args.getPingSocket();
  }
  */
  
  public void init(Runnable init) // Consumer<ServerBuilder> init)
  {
    Objects.requireNonNull(init);
    
    _initList.add(init);
  }
  
  public void init(ServerBase server)
  {
    /*
    if (HeapDump.isAvailable()) {
      _heapDump = HeapDump.create();
    }
    
    _mbeanServer = JmxUtil.getMBeanServer();
    
    try {
      _hotSpotName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
      _heapDumpArgs = new String[] { String.class.getName(), boolean.class.getName() };
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    */
  }

  /*
  public static ServerBuilder create(String[] args)
  {
    return create(new ArgsServerBase(args));
  }

  public static ServerBuilder create(ArgsServerBase args)
  {
    throw new UnsupportedOperationException();
  }
  */

  String clusterId()
  {
    return "cluster";
  }
  
  /*
  String getClusterId()
  {
    return _serverConfig.getCluster().getId();
  }
  */
  
  /*
  protected RootConfigBoot getRootConfig()
  {
    return getConfigBoot().getRoot();
  }
  */

  /*
  PathImpl getHomeDirectory()
  {
    return _args.getHomeDirectory();
  }
  */
  
  Path getRootDirectory()
  {
    Path rawRoot = config().get("baratine.root", Path.class, 
                                Paths.get("/tmp/baratine"));

    //String name = getClusterId() + '-' + _serverConfig.getPort();
    
    //return rawRoot.lookup(name);
    
    return rawRoot;
  }

  public Path getLogDirectory()
  {
    return config().get("log.root", Path.class, getRootDirectory().resolve("log"));
  }

  public void setShutdownWaitTime(long period)
  {
    _shutdownWaitTime = period;
  }
  
  public long getShutdownWaitTime()
  {
    return _shutdownWaitTime;
  }

  public ServerBartender serverSelf()
  {
    Objects.requireNonNull(_serverSelf);
    
    return _serverSelf;
  }

  public void serverSelf(ServerBartender serverSelf)
  {
    Objects.requireNonNull(serverSelf);
    
    _serverSelf = serverSelf;
  }

  /*
  PathImpl getConfigPath()
  {
    PathImpl confPath = _args.getConfigPath();

    if (confPath == null) {
      confPath = _args.getConfigPathDefault();
    }

    return confPath;
  }
  */

  /*
  public StatProbeManager getStatProbeManager()
  {
    return _statProbeManager;
  }
  */

  public WebServerImpl build()
  {
    EnvLoader.init();
    
    _systemManager = createSystemManager();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    boolean isValid = false;
    try {
      thread.setContextClassLoader(_systemManager.getClassLoader());
      
      Vfs.setPwd(getRootDirectory());
      
      /*
      if (! isEmbedded()) {
        logCopyright();
      }
      */
      logCopyright();

      preConfigureInit();
      
      configureRootDirectory();
      
      _serverSelf = initNetwork();
      Objects.requireNonNull(_serverSelf);
      
      addServices(); // _serverSelf);

      initHttpSystem(_systemManager, _serverSelf);
      
      WebServerImpl server = new WebServerImpl(this);
      /*
      build(systemManager,
            _serverSelf);
            */

      // init(server);
      
      isValid = true;
      
      _systemManager.start();
      
      return server;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    } finally {
      thread.setContextClassLoader(oldLoader);
      
      if (! isValid) {
        _systemManager.shutdown(ShutdownModeAmp.IMMEDIATE);
      }
    }
  }

  public boolean isClosed()
  {
    return _serverSelf != null;
  }
  
  private void logCopyright()
  {
    if (CurrentTime.isTest() || config().get("quiet",boolean.class,false)) {
      return;
    }
    
    System.out.println(Version.getFullVersion());
    System.out.println(Version.getCopyright());
    System.out.println();

    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    Instant instant = Instant.ofEpochMilli(startTime());

    System.out.println("Starting " + getProgramName()
                       + " on " + formatter.format(instant.atZone(ZoneId.systemDefault())));
    System.out.println();
  }

  /*
  protected ServerBase build(SystemManager systemManager,
                             ServerBartender serverSelf)
    throws Exception
  {
    return new ServerBase(this, systemManager, serverSelf);
  }
  */

  /**
   * Must be called after the Resin.create()
   */
  void preConfigureInit()
  {
    // _resinLocal.set(this, getClassLoader());

    /*
    if (getRootDirectory() == null) {
      throw new NullPointerException();
    }
    */

    /*
    if (isEmbedded()) {
      String serverId = getServerIdVar(); // getServerDisplayName();

      // JmxUtilResin.addContextProperty("Server", serverId);
    }
    */

    addPreTopologyServices();

      /*
      if (! isWatchdog()) {
        // server/p603
        initRepository();
      }
      */

      // watchdog/0212
      // else
      //  setRootDirectory(Vfs.getPwd());

    /*
    if (! isWatchdog()) {
      // Environment.addChildLoaderListener(new ListenerPersistenceEnvironment());
      // Environment.addChildLoaderListener(new CdiAddLoaderListener());
      // Environment.addChildLoaderListener(new EjbEnvironmentListener());
    }
    */

    InjectorAmp.create();
    
    //initCdiEnvironment();

    // readUserProperties();

    //Config.setProperty("rvar0", getServerId()); // getServerDisplayName());
    //Config.setProperty("rvar1", getClusterId()); // getServerDisplayName());
      /*
      _bootConfig
        = new BootConfig(_resinSystem,
                         getDisplayServerId(),
                         getResinHome(),
                         getRootDirectory(),
                         getLogDirectory(),
                         getResinConf(),
                         isProfessional(),
                         isWatchdog() ? BootType.WATCHDOG : BootType.RESIN);

     _bootResinConfig = _bootConfig.getBootResin();
     */
  }

  /*
  protected ServerBuilder createDelegate()
  {
    return ServerBuilder.create(this);
  }
  */
  
  private String getServerIdVar()
  {
    String serverId = config().get("server.id");
    
    if (serverId == null) {
      int port = portServer();
      
      if (port > 0) { // && ! _serverConfig.isEphemeral()) {) {
        serverId = "embed-" + port;
      }
      else {
        serverId = "embed";
      }
    }
    
    return serverId;
  }

  /*
  protected void initCdiEnvironment()
  {
  }
  */
  
  /*
  protected void addChampSystem(ServerBartender selfServer)
  {
    // ChampSystem.createAndAddSystem(selfServer);
  }
  */
  
  protected void addJournalSystem()
  {
    //JournalSystem.createAndAddSystem();
  }

  /**
   * Configures the selected server from the boot config.
   */
  protected void initHttpSystem(SystemManager system,
                                ServerBartender selfServer)
    throws IOException
  {
    //RootConfigBoot rootConfig = getRootConfig();
    
    String clusterId = selfServer.getClusterId();
    
    //ClusterConfigBoot clusterConfig = rootConfig.findCluster(clusterId);
    
    String serverHeader = config().get("server.header");
    
    if (serverHeader != null) {
    }
    else if (! CurrentTime.isTest()) {
      serverHeader = getProgramName() + "/" + Version.getVersion();
    }
    else {
      serverHeader = getProgramName() + "/1.1";
    }
    
    // XXX: need cleaner config class names (root vs cluster at minimum)
    /*
    ServerContainerConfig serverConfig
      = new ServerContainerConfig(this, system, selfServer);
      */

    // rootConfig.getProgram().configure(serverConfig);
    
    //clusterConfig.getProgram().configure(serverConfig);

    /*
    ServerConfig config = new ServerConfig(serverConfig);
    clusterConfig.getServerDefault().configure(config);

    ServerConfigBoot serverConfigBoot
      = rootConfig.findServer(selfServer.getDisplayName());

    if (serverConfigBoot != null) {
      serverConfigBoot.getServerProgram().configure(config);
    }
    */

    /*
    _args.getProgram().configure(config);
    */
    // _bootServerConfig.getServerProgram().configure(config);

    // serverConfig.init();
    
    //int port = getServerPort();

    HttpContainerBuilder httpBuilder
      = createHttpBuilder(selfServer, serverHeader);
    
    // serverConfig.getProgram().configure(httpBuilder);

    //httpBuilder.init();
    
    //PodSystem.createAndAddSystem(httpBuilder);
    HttpSystem.createAndAddSystem(httpBuilder);
    
    //return http;
  }

  private HttpContainerBuilder createHttpBuilder(ServerBartender selfServer,
                                                 String serverHeader)
  {
    HttpBaratineBuilder builder
      = new HttpBaratineBuilder(config(), selfServer, serverHeader);
    
    for (IncludeWebAmp include : _includes) {
      builder.include(include);
    }
    
    return builder;
  }

  /*
  public void setEmbedded(boolean isEmbedded)
  {
    _isEmbedded = isEmbedded;
  }
  
  public boolean isEmbedded()
  {
    return _isEmbedded;
  }
  
  protected boolean isWatchdog()
  {
    return _isWatchdog;
  }
  */

  /*
  private ConfigBoot getConfigBoot()
  {
    return _configBoot;
  }
  */

  /**
   * Configures the root directory and dataDirectory.
   */
  void configureRootDirectory()
    throws IOException
  {
    _dataDirectory = calculateDataDirectory();

    RootDirectorySystem.createAndAddSystem(getRootDirectory(), _dataDirectory);
  }

  protected Path getDataDirectory()
  {
    Objects.requireNonNull(_dataDirectory);
    
    return _dataDirectory;
  }
  
  private Path calculateDataDirectory()
  {
    Path root = getRootDirectory();
    
    Path path;
    /* XXX:
    if (_resinDataDirectory != null)
      root = _resinDataDirectory;
      */

    /*
    if (isWatchdog()) {
      path = root.lookup("watchdog-data");
    }
    else {
      path = root.lookup("baratine-data");
    }
    */
    
    int serverPort = portServer();

    /*
    if (root instanceof MemoryPath) { // QA
      root = WorkDir.getTmpWorkDir().lookup("qa");
    }
    */
    
    boolean isRemoveOnStart
      = config().get("baratine.server.remove-data-on-start", boolean.class, false);
    
    if (serverPort > 0 && ! isEphemeral()) {
      path = root.resolve("data-" + serverPort);
      
      if (isRemoveOnStart) {
        removeDataDirectory(path);
      }
    }
    else if (serverPort < 0 && ! isEphemeral()) {
      path = root.resolve("data-embed");
      
      if (isRemoveOnStart) {
        removeDataDirectory(path);
      }
    }
    else {
      return openDynamicDataDirectory(root);
    }

    return path;
  }
  
  private boolean isEphemeral()
  {
    // TODO Auto-generated method stub
    return false;
  }

  private Path openDynamicDataDirectory(Path root)
  {
    cleanDynamicDirectory(root);
    
    for (int i = 0; i < 10000; i++) {
      Path dir = root.resolve("data-dyn-" + i);
      
      if (! Files.exists(dir) || RootDirectorySystem.isFree(dir)) {
        try {
          Files.createDirectories(dir);
        } catch (Exception e) {
          log.log(Level.FINER, e.toString(), e);
        }
        
        _dynamicDataIndex  = i;
        
        return dir;
      }
    }
    
    throw new IllegalStateException(L.l("Can't create working directory."));
  }
  
  private void cleanDynamicDirectory(Path root)
  {
    /*
    String []list;
    
    try {
      list = root.list();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      return;
    }
    */
    
    try (DirectoryStream<Path> dirIter = Files.newDirectoryStream(root)) {
      for (Path dir : dirIter) {
        if (! dir.getFileName().toString().startsWith("data-dyn-")) {
          continue;
        }
        
        if (! RootDirectorySystem.isFree(dir)) {
          continue;
        }

        IoUtil.removeAll(dir);
        // Files.walkFileTree(dir, x->Files.deleteIfExists(x));
        //dir.removeAll();
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  private void removeDataDirectory(Path path)
  {
    String name = "data-" + portServer();
    
    if (! path.getFileName().toString().equals(name)) {
      return;
    }
    
    if (! RootDirectorySystem.isFree(path)) {
      return;
    }
    
    try {
      IoUtil.removeAll(path);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  protected ServerBartender initNetwork()
    throws Exception
  {
    SystemManager systemManager = SystemManager.getCurrent();
    
    /*
    if (getClusterSystemKey() != null) {
      SecuritySystem security = SecuritySystem.getCurrent();
      security.setSignatureSecret(getClusterSystemKey());
    }
    */
    
    ServerSocketBar ss = null;
    
    if (portServer() == 0) {
      //ss = _serverConfig.getServerSocket();
      //ss = openEphemeralServerSocket();
      ss = null;
      
      if (true) throw new UnsupportedOperationException();

      if (ss != null) {
        _serverPort = ss.getLocalPort();
      }
      else {
        throw new IllegalStateException(L.l("server-port 0 requires an ephemeral port"));
      }
    }
    
    ServerBartender serverSelf = initBartender();
    NetworkSystem networkSystem = NetworkSystem.createAndAddSystem(systemManager,
                                                                   serverSelf,
                                                                   config());
    if (ss != null) {
      networkSystem.bind(addressServer(),
                         ss.getLocalPort(),
                         ss);
    }
    /*
    else if (getServerPort() > 0) {
      int serverPort = getServerPort();
      
      PortTcpBuilder tcpBuilder = new PortTcpBuilder(env());
      tcpBuilder.portName("server");
      tcpBuilder.protocol(new HttpProtocol());
      
      networkSystem.addPort(tcpBuilder.get());
    }
    */
    
    //int serverPort = getServerPort();

    /*
    if (_args != null) {
      for (BoundPort port : _args.getBoundPortList()) {
        networkSystem.bind(port.getAddress(),
                           port.getPort(),
                           port.getServerSocket());
      }
    }
    */
    
    // DeploySystem.createAndAddSystem();
    //DeploySystem2.createAndAddSystem();
    
    return serverSelf;
  }
  
  /*
  private QServerSocket openEphemeralServerSocket()
  {
    int port = getServerPort();
    
    if (port != 0) {
      throw new IllegalStateException();
    }
    
    try {
      QServerSocket ss = QJniServerSocket.create(0, 0);
      
      _serverPort = ss.getLocalPort();
      
      ServerConfigBoot serverConfig = getServerConfig();
      
      serverConfig.setPort(_serverPort);
      serverConfig.setEphemeral(true);;
      
      return ss;
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }
  */

  protected void addServices()
  {
    TempStoreSystem.createAndAddSystem();
    
    // XXX: KrakenSystem.createAndAddSystem(selfServer);
    
    // XXX: BartenderFileSystem.createAndAddSystem();
    
    for (Runnable init : _initList) {
      init.run(); // accept(this);
    }
    
    addJournalSystem();
  }

  /**
   * Configures the selected server from the boot config.
   * @return 
   */
  protected ServerBartender initBartender()
  {
    // BootResinConfig bootResin = _bootResinConfig;

    // XXX: _clusterSystemKey = _bootConfig.getClusterSystemKey(_args);
    
    return new ServerBartenderSelf(addressServer(), portServer());
  }
  
  protected ServerBartender initBartenderCluster()
  {
    // BootResinConfig bootResin = _bootResinConfig;

    // XXX: _clusterSystemKey = _bootConfig.getClusterSystemKey(_args);
    
    int machinePort = portServer();
    
    if (_dynamicDataIndex >= 0) {
      machinePort = _dynamicDataIndex;
    }
    
    int portBartender = getPortBartender();
    /*
    ServerHeartbeatBuilder selfBuilder = new ServerHeartbeatBuilder();
    
    if (_config.get("client", boolean.class, false)) {
      selfBuilder.pod("client");
    }
    */
    
    /* XXX:
    for (String pod : _args.getArgList("pod")) {
      selfBuilder.pod(pod);
    }
    
    if (_args.getArgFlag("pod-any")) {
      selfBuilder.podAny(true);
    }
    */
    
    /*
    String clusterId = "cluster";

    BartenderBuilder builder
      = BartenderSystem.newSystem(_config,
                                      getServerAddress(),
                                      getServerPort(),
                                      isSSL(),
                                      portBartender,
                                      clusterId,
                                      getServerId(),
                                      machinePort,
                                      selfBuilder);
                                      */

    //initTopologyStatic(builder);
    
    //BartenderSystem system = builder.build();
    
    /*
    initTopology(builder, 
                 getServerId(),
                 _serverConfig.getCluster().getId(), 
                 _serverConfig.getPort());
                 */
    
    //return system.serverSelf();
    return null;
  }

  protected String getProgramName()
  {
    return "Baratine";
  }

  /**
   * Dump threads for debugging
   */
  public void dumpThreads()
  {
    //ThreadDump.create().dumpThreads();
  }

  /**
   * Dump heap on exit.
   */
  public void dumpHeapOnExit(ServerBase server)
  {
    /*
    RootDirectorySystem rootService
      = server.getSystemManager().getSystem(RootDirectorySystem.class);
    
    if (rootService != null && _mbeanServer != null) {
      try {
        String pathName = rootService.getDataDirectory().lookup("resin.hprof").getNativePath();

        _mbeanServer.invoke(_hotSpotName, "dumpHeap", 
                            new Object[] { pathName, true },
                            _heapDumpArgs);
        
        log.warning("Java Heap dumped to " + pathName);
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    if (_heapDump != null) {
      _heapDump.logHeapDump(log, Level.SEVERE);
    }
    */
  }

  protected NetworkSystem createNetworkSystem(SystemManager systemManager,
                                              ServerBartender selfServer)
  {
    return new NetworkSystem(systemManager,
                             selfServer,
                             config());
  }

  /**
   * 
   */
  protected void addPreTopologyServices()
  {
    WarningSystem.createAndAddSystem();
    
    //ShutdownSystem.createAndAddSystem(isEmbedded());
    boolean isEmbedded = true;
    ShutdownSystem.createAndAddSystem(isEmbedded);
    
    AmpSystem.createAndAddSystem(getServerId());
    //DeploySystem.createAndAddSystem();
    DeploySystem2.createAndAddSystem();

    /*
    SecuritySystem security = SecuritySystem.createAndAddSystem();
    
    String clusterKey = config().get("baratine.cluster.key");
    
    security.setSignatureSecret(clusterKey);
    */
    
    // HealthStatusService.createAndAddService();
    
    // BlockManagerSubSystem.createAndAddService();

    //createKrakenStoreSystem();
      
    // ShutdownSystem.getCurrent().addMemoryFreeTask(new BlockManagerMemoryFreeTask());
    
    /*
    if (! isWatchdog()) {
      HealthSubSystem health = HealthSubSystem.createAndAddSystem();
      
      if (isEmbedded()) {
        health.setEnabled(false);
      }
    }
    */
  }
  
  protected String getDynamicServerAddress()
  {
    return  null;
  }

  protected int portServer()
  {
    return config().get("server.port", int.class, -1);
    /*
    if (_serverPort > 0) {
      return _serverPort;
    }
    else {
      return _serverConfig.getPort();
    }
    */
  }

  protected boolean isSSL()
  {
    if (_isSSL) {
      return _isSSL;
    }
    else if (config().get("server.ssl") != null) {
      return true;
    }
    else if (config().get("server.openssl") != null) {
      return true;
    }
    else {
      return false;
    }
  }

  protected int getPortBartender()
  {
    if (_portBartender > 0) {
      return _portBartender;
    }

    //int port = _serverConfig.getPortBartender();
    
    int port = config().get("bartender.port", int.class, 0);
      
    if (port > 0) {
      return port;
    }

    try {
      SocketSystem socketSystem = SocketSystem.current();
      //JniServerSocketFactory ssFactory = new JniServerSocketFactory();
      
      _ssBartender = socketSystem.openServerSocket(0);
    
      //_serverConfig.setSocketBartender(ssBartender);
      //_serverConfig.setBartenderPort(ssBartender.getLocalPort());
    
      //return _serverConfig.getPortBartender();
      
      return 0;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  protected String addressServer()
  {
    // address needs to be internal address, not the configured external one.
    // The external address will be discovered by the join protocol.
    String address = SocketSystem.current().getHostAddress();
    
    return address; 
  }

  protected String getServerDisplayName()
  {
    //return _serverConfig.getDisplayName();
    return "dummy";
  }

  protected String getServerId()
  {
    String sid = null;//_serverConfig.getId();
    
    if (sid != null && ! "".equals(sid)) {
      return sid;
    }
    
    sid = config().get("server.id");
    
    if (sid != null && ! "".equals(sid)) {
      return sid;
    }
    
    int port = portServer();
    
    if (port <= 0) {
      return clusterId() + "-embed";
    }
    
    String address = addressServer();
    
    if ("".equals(address)) {
      address = SocketSystem.current().getHostAddress();
    }
    
    // return getClusterId() + '-' + _serverConfig.getPort();
    return address + ":" + port;
  }

  protected String getClusterSystemKey()
  {
    return null; // XXX:
  }

  SystemManager createSystemManager()
  {
    String serverId = getServerId();
    
    return new SystemManager(serverId, parentClassLoader());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
  
  private static class ServerBartenderSelf extends ServerBartender
  {
    private ClusterBartender _cluster = new ClusterBartenderSelf();
    
    ServerBartenderSelf(String address, int port)
    {
      super(address, port);
    }
    
    @Override
    public String getDisplayName()
    {
      return getAddress() + ":" + port();
    }

    @Override
    public ClusterBartender getCluster()
    {
      return _cluster;
    }

    @Override
    public ServerBartenderState getState()
    {
      return ServerBartenderState.up;
    }
    
  }
  
  private static class ClusterBartenderSelf extends ClusterBartender
  {
    ClusterBartenderSelf()
    {
      super("cluster");
    }

    @Override
    public RootBartender getRoot()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends ServerBartender> getServers()
    {
      throw new UnsupportedOperationException();
    }
  }
  
  private static class ViewGen implements IncludeWebAmp
  {
    private ViewRender<?> _view;
    
    ViewGen(ViewRender<?> view)
    {
      Objects.requireNonNull(view);
      
      _view = view;
    }

    @Override
    public void build(WebBuilderAmp builder)
    {
      builder.view(_view);
    }
  }
  
  private static class ViewClass implements IncludeWebAmp
  {
    private Class<? extends ViewRender<?>> _viewClass;
    
    ViewClass(Class<? extends ViewRender<?>> viewClass)
    {
      Objects.requireNonNull(viewClass);
      
      _viewClass = viewClass;
    }

    @Override
    public void build(WebBuilderAmp builder)
    {
      builder.view((Class) _viewClass);
    }
  }

  @Override
  public InjectorBuilder autoBind(InjectAutoBind autoBind)
  {
    throw new UnsupportedOperationException();
  }
  
  private static class ConvertStringToPath
    implements Convert<String,Path>
  {
    @Override
    public Path convert(String source)
    {
      return Vfs.path(source);
    }
  }
  
  class SslBuilderImpl implements SslBuilder
  {
  }
}
