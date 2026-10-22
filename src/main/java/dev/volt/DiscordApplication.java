package dev.volt;

import java.util.Properties;

/** Public project application ID, supplied once by the publisher, never by each player. */
public final class DiscordApplication {
    private DiscordApplication(){}
    private static final String BUNDLED=read();
    private static String read(){
        try(var in=DiscordApplication.class.getResourceAsStream("/volt-discord.properties")){
            if(in==null)return "";Properties p=new Properties();p.load(in);return valid(p.getProperty("applicationId",""));
        }catch(Exception ignored){return "";}
    }
    static String valid(String id){return id!=null&&id.matches("[0-9]{16,22}")?id:"";}
    public static String id(VoltConfig config){return BUNDLED.isEmpty()?valid(config.discordApplicationId):BUNDLED;}
    public static boolean available(VoltConfig config){return !id(config).isEmpty();}
}
