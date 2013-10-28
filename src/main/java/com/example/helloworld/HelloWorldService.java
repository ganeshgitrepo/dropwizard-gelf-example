package com.example.helloworld;

import com.example.helloworld.auth.ExampleAuthenticator;
import com.example.helloworld.cli.RenderCommand;
import com.example.helloworld.core.Person;
import com.example.helloworld.core.Template;
import com.example.helloworld.core.User;
import com.example.helloworld.db.PersonDAO;
import com.example.helloworld.health.TemplateHealthCheck;
import com.example.helloworld.resources.HelloWorldResource;
import com.example.helloworld.resources.PeopleResource;
import com.example.helloworld.resources.PersonResource;
import com.example.helloworld.resources.ProtectedResource;
import com.example.helloworld.resources.ViewResource;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.assets.AssetsBundle;
import com.yammer.dropwizard.auth.basic.BasicAuthProvider;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.db.DatabaseConfiguration;
import com.yammer.dropwizard.hibernate.HibernateBundle;
import com.yammer.dropwizard.migrations.MigrationsBundle;
import com.yammer.dropwizard.views.ViewBundle;
import net.gini.dropwizard.gelf.bundles.GelfLoggingBundle;
import net.gini.dropwizard.gelf.config.GelfConfiguration;
import net.gini.dropwizard.gelf.logging.GelfBootstrap;
import net.gini.dropwizard.gelf.logging.UncaughtExceptionHandlers;

public class HelloWorldService extends Service<HelloWorldConfiguration> {

    private static final String NAME = "hello-world";
    private static final String GELF_HOST = System.getProperty("dw.gelf.host", "localhost");
    private static final int GELF_PORT = Integer.getInteger("dw.gelf.port", 12201);

    public static void main(String[] args) throws Exception {
        GelfBootstrap.bootstrap(NAME, GELF_HOST, GELF_PORT, false);
        Thread.currentThread().setUncaughtExceptionHandler(UncaughtExceptionHandlers.systemExit());
        new HelloWorldService().run(args);
    }

    private final HibernateBundle<HelloWorldConfiguration> hibernateBundle =
            new HibernateBundle<HelloWorldConfiguration>(Person.class) {
                @Override
                public DatabaseConfiguration getDatabaseConfiguration(HelloWorldConfiguration configuration) {
                    return configuration.getDatabaseConfiguration();
                }
            };

    private final GelfLoggingBundle<HelloWorldConfiguration> gelfLoggingBundle =
            new GelfLoggingBundle<HelloWorldConfiguration>() {
                @Override
                public GelfConfiguration getConfiguration(HelloWorldConfiguration configuration) {
                    return configuration.getGelfConfiguration();
                }
            };

    @Override
    public void initialize(Bootstrap<HelloWorldConfiguration> bootstrap) {
        bootstrap.setName(NAME);
        bootstrap.addCommand(new RenderCommand());
        bootstrap.addBundle(new AssetsBundle());
        bootstrap.addBundle(new MigrationsBundle<HelloWorldConfiguration>() {
            @Override
            public DatabaseConfiguration getDatabaseConfiguration(HelloWorldConfiguration configuration) {
                return configuration.getDatabaseConfiguration();
            }
        });
        bootstrap.addBundle(hibernateBundle);
        bootstrap.addBundle(gelfLoggingBundle);
        bootstrap.addBundle(new ViewBundle());
    }

    @Override
    public void run(HelloWorldConfiguration configuration,
                    Environment environment) throws ClassNotFoundException {
        final PersonDAO dao = new PersonDAO(hibernateBundle.getSessionFactory());

        environment.addProvider(new BasicAuthProvider<User>(new ExampleAuthenticator(),
                "SUPER SECRET STUFF"));

        final Template template = configuration.buildTemplate();

        environment.addHealthCheck(new TemplateHealthCheck(template));
        environment.addResource(new HelloWorldResource(template));
        environment.addResource(new ViewResource());
        environment.addResource(new ProtectedResource());

        environment.addResource(new PeopleResource(dao));
        environment.addResource(new PersonResource(dao));
    }
}
