package app;

import gui.WebServer;

import static spark.Spark.*;

public class Launcher {

    public static void main(String[] args) {
        port(8080);
        staticFiles.location("/public");
        init();
        new WebServer();
    }
}