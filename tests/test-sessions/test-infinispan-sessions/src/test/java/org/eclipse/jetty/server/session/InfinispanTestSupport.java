//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//


package org.eclipse.jetty.server.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.annotation.ElementType;
import java.util.Properties;

import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.util.IO;
import org.hibernate.search.cfg.Environment;
import org.hibernate.search.cfg.SearchMapping;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

/**
 * InfinispanTestSupport
 *
 *
 */
public class InfinispanTestSupport
{
    public static final String DEFAULT_CACHE_NAME =  "session_test_cache";
    public  Cache _cache;
 
    public ConfigurationBuilder _builder;
    private  File _tmpdir;
    private boolean _useFileStore;
    private String _name;
    public static  EmbeddedCacheManager _manager;
    
    static
    {
        try
        {
            _manager = new DefaultCacheManager(new GlobalConfigurationBuilder().globalJmxStatistics().allowDuplicateDomains(true).build());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    
    
    
    public InfinispanTestSupport ()
    {
        this (null);
    }
    
    public InfinispanTestSupport(String cacheName)
    {     
        if (cacheName == null)
            cacheName = DEFAULT_CACHE_NAME+System.currentTimeMillis();
        
        _name = cacheName;
        _builder = new ConfigurationBuilder();
    }
    
    public void setUseFileStore (boolean useFileStore)
    {
        _useFileStore = useFileStore;
    }
  
    public Cache getCache ()
    {
        return _cache;
    }
    
    public void setup () throws Exception
    {
        File testdir = MavenTestingUtils.getTargetTestingDir();
        File tmp = new File (testdir, "indexes");
        IO.delete(tmp);
        tmp.mkdirs();
        
        SearchMapping mapping = new SearchMapping();
        mapping.entity(SessionData.class).indexed().providedId().property("expiry", ElementType.FIELD).field();
        Properties properties = new Properties();
        properties.put(Environment.MODEL_MAPPING, mapping);
        properties.put("hibernate.search.default.indexBase", tmp.getAbsolutePath());
        
        if (_useFileStore)
        {      
            _tmpdir = File.createTempFile("infini", "span");
            _tmpdir.delete();
            _tmpdir.mkdir();
            
            Configuration config = _builder.indexing()
                                           .index(Index.ALL)
                                           .addIndexedEntity(SessionData.class)
                                           .withProperties(properties)
                                           .persistence()
                                           .addSingleFileStore()
                                           .location(_tmpdir.getAbsolutePath())
                                           .storeAsBinary()
                                           .build();
            
            _manager.defineConfiguration(_name, config);
        }
        else
        {
            _manager.defineConfiguration(_name, _builder.indexing()
                                                        .withProperties(properties)
                                                        .index(Index.ALL)
                                                        .addIndexedEntity(SessionData.class)
                                                        .build());
        }
        _cache = _manager.getCache(_name);
    }


    public void teardown () throws Exception
    {
        _cache.clear();
        _manager.removeCache(_name);
        if (_useFileStore)
        {
            if (_tmpdir != null)
            {
                IO.delete(_tmpdir);
            }
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void createSession (SessionData data)
    throws Exception
    {
        _cache.put(data.getContextPath()+"_"+data.getVhost()+"_"+data.getId(), data);
    }

    
    public void createUnreadableSession (SessionData data)
    {
        
    }
    
    
    public boolean checkSessionExists (SessionData data)
    throws Exception
    {
        return (_cache.get(data.getContextPath()+"_"+data.getVhost()+"_"+data.getId()) != null);
    }
    
    
    public boolean checkSessionPersisted (SessionData data)
    throws Exception
    {
        Object obj = _cache.get(data.getContextPath()+"_"+data.getVhost()+"_"+data.getId());
        if (obj == null)
            return false;
        
        SessionData saved = (SessionData)obj;
        
        
        //turn an Entity into a Session
        assertEquals(data.getId(), saved.getId());
        assertEquals(data.getContextPath(), saved.getContextPath());
        assertEquals(data.getVhost(), saved.getVhost());
        assertEquals(data.getAccessed(), saved.getAccessed());
        assertEquals(data.getLastAccessed(), saved.getLastAccessed());
        assertEquals(data.getCreated(), saved.getCreated());
        assertEquals(data.getCookieSet(), saved.getCookieSet());
        assertEquals(data.getLastNode(), saved.getLastNode());
        //don't test lastSaved, because that is set only on the SessionData after it returns from SessionDataStore.save()
        assertEquals(data.getExpiry(), saved.getExpiry());
        assertEquals(data.getMaxInactiveMs(), saved.getMaxInactiveMs());

        //same number of attributes
        assertEquals(data.getAllAttributes().size(), saved.getAllAttributes().size());
        //same keys
        assertTrue(data.getKeys().equals(saved.getKeys()));
        //same values
        for (String name:data.getKeys())
        {
            assertTrue(data.getAttribute(name).equals(saved.getAttribute(name)));
        }
        
        return true;
    }
}
