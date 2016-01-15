package common;

import java.io.FileInputStream;
import java.util.Properties;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConfigLoader implements ServletContextListener

{

	public static Properties props;

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		props = new Properties();
		try {
			String path = (getClass().getClassLoader().getResource("").toURI()).getPath();
			FileInputStream fis = new FileInputStream(path + "/config.props");
			props.load(fis);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
