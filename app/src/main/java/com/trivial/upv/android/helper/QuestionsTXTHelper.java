package com.trivial.upv.android.helper;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.trivial.upv.android.R;
import com.trivial.upv.android.helper.singleton.StringRequestHeaders;
import com.trivial.upv.android.helper.singleton.VolleySingleton;
import com.trivial.upv.android.model.JsonAttributes;
import com.trivial.upv.android.model.json.CategoryJSON;
import com.trivial.upv.android.model.quiz.Quiz;
import com.trivial.upv.android.model.txtquiz.QuestionTXT;
import com.trivial.upv.android.model.txtquiz.QuestionsTXT;
import com.trivial.upv.android.persistence.TopekaJSonHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.trivial.upv.android.persistence.TopekaJSonHelper.createArrayIntFromNumQuizzes;


/**
 * Created by jvg63 on 30/07/2017.
 */

public class QuestionsTXTHelper {

    //    public static String JsonURL = "https://trivialandroid-d2b33.firebaseio.com/.json";
//    public static String JsonURL = "http://eventosjvg.esy.es/categories_upv.json";
    public static String JsonURL = "http://mmoviles.upv.es/trivial/trivialandroid.json";

    Context mContext;


    public QuestionsTXTHelper(Context signInActivity) {
        mContext = signInActivity;
    }

    /**
     * Dada la descarga de una url de texto plano(.txt), las adapta a una clase Quizz
     *
     * @param category
     * @param preguntasTXT
     * @throws UnsupportedEncodingException
     */
    public void getQuizzesFromString(CategoryJSON category, String preguntasTXT, String url) throws UnsupportedEncodingException {
        String line;

        QuestionTXT questionTXT = null;
        QuestionsTXT questionsTXT = new QuestionsTXT();
        List<Quiz> quizzes = new ArrayList<>();


        String tmpPreguntasTXT = preguntasTXT.replaceAll("(\\r?\\n\\r?\\n)(\\r?\\n)*","$1");

        String[] preguntas = tmpPreguntasTXT.split("\\r?\\n");
        int contador = 0;

        for (; contador < preguntas.length; contador++) {
            line = removeWordsUnWanted(preguntas[contador], url); // Primera linea: Temática
            if (contador == 0) {
                questionTXT = new QuestionTXT();
                questionsTXT.setSubject(line);
            }
            // Si linea en blanco. Nueva pregunta
            else if (line.isEmpty()) {
                questionsTXT.getQuestions().add(questionTXT);
                questionTXT = new QuestionTXT();

            } else {

                // Respuestas
                if (questionTXT.getEnunciado() != null) {
                    String[] datos = line.split("#");
                    // Hay Comentario
                    if (datos.length > 1) {
                        questionTXT.getComentariosRespuesta().add(datos[1]);
                    } else {
                        questionTXT.getComentariosRespuesta().add("");
                    }

                    if (datos[0].charAt(0) == '*') {
                        questionTXT.getRespuestaCorrecta().add(questionTXT.getRespuestas().size());
                        questionTXT.getRespuestas().add(datos[0].substring(1));
                    } else {
                        questionTXT.getRespuestas().add(datos[0]);
                    }
                }
                // Enunciados
                else {
                    questionTXT.setEnunciado(line);
                }
            }
        }
        if (contador > 0 && questionTXT.getEnunciado() != null) {
            questionsTXT.getQuestions().add(questionTXT);
        }

        category.setDescription(questionsTXT.getSubject());
        category.setCategory(questionsTXT.getSubject());
        category.setMoreinfo(questionsTXT.getSubject());

        Quiz quizz;
        for (QuestionTXT question : questionsTXT.getQuestions()) {
            if (question.getRespuestaCorrecta().size() > 1) {
                quizz = TopekaJSonHelper.createQuizDueToType(question, JsonAttributes.QuizType.MULTI_SELECT);
            } else if (question.getRespuestas().size() == 4) {
                quizz = TopekaJSonHelper.createQuizDueToType(question, JsonAttributes.QuizType.FOUR_QUARTER);
            } else {
                quizz = TopekaJSonHelper.createQuizDueToType(question, JsonAttributes.QuizType.SINGLE_SELECT);
            }
            quizzes.add(quizz);
        }
        category.setQuizzes(quizzes);
        category.setScore(createArrayIntFromNumQuizzes(category));
    }

    private synchronized void addRequest() {
        pendingRequests++;
        maxPendingRequests++;
    }

    int pendingRequests = 0;
    int maxPendingRequests = 0;

    // change < and > for &lt; and &gt where there aren't a tag HTML
    private String substHtmlCharacters(String str) {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch == '>' && i != 0) {
                char c = str.charAt(i - 1);
                if (Character.isWhitespace(c) || !Character.isLetter(c)) {
                    sBuilder.append("&gt;");
                } else
                    sBuilder.append(ch);
            } else if (ch == '>' && i == 0) {
                sBuilder.append("&gt;");
            } else if (ch == '<' && i < str.length() - 1) {
                char c = str.charAt(i + 1);
                if (!(c == '/' || Character.isLetter(c))) {
                    sBuilder.append("&lt;");
                } else
                    sBuilder.append(ch);
            } else if (ch == '<' && i == str.length() - 1) {
                sBuilder.append("&lt;");
            } else {
                sBuilder.append(ch);
            }
        }
        return sBuilder.toString();
    }

    // Process de strings and remove 2 continuous \n. Also change < and > for &lt; and &gt where there aren't a tag HTML
    private String removeWordsUnWanted(String line, String url) {
        String lineTmp = line.replaceAll("<br><br>", "<br>");
        lineTmp = lineTmp.replaceAll("</br>", "<br>");
        lineTmp = lineTmp.replaceAll("<br/>", "<br>");
        lineTmp = substHtmlCharacters(lineTmp);

        lineTmp = addPathToUrlImg(lineTmp, url);


        return lineTmp;
    }

    private String addPathToUrlImg(String input, String url) {

        String regex = "(<img\\s+src=[\"'])([^\"']+)";

        String replace = "$1" + url.substring(0, url.lastIndexOf("/") + 1) + "$2";

        Pattern p = Pattern.compile(regex);

        // get a matcher object
        Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            if (!input.substring(m.start(), m.end()).contains("http"))
                m.appendReplacement(sb, replace);
        }

        m.appendTail(sb);

        return sb.toString();
    }


    // Auxiliary functions to remove TAGS
    private static final Pattern REMOVE_TAGS = Pattern.compile("<.+?>");

    public static String removeTags(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }

        Matcher m = REMOVE_TAGS.matcher(string);
        return m.replaceAll("");
    }

    private void updateProgress() {

        synchronized (this) {
            removeRequest();

            if (pendingRequests == 0) {
                Log.d("CARGA", "CARGA_FINALIZADA");

                new Thread() {
                    public void run() {
                        TopekaJSonHelper.getInstance(mContext, false).updateCategory();
                        TopekaJSonHelper.getInstance(mContext, false).setLoaded(true);
                        TopekaJSonHelper.getInstance(mContext, false).sendBroadCastMessageRefresh(100);
                        TopekaJSonHelper.getInstance(mContext, false).sendBroadCastMessage("OK");
                    }
                }.start();
            }
//        Log.d("CARGA", "PENDING:" + pendingRequests);
            else {
                TopekaJSonHelper.getInstance(mContext, false).sendBroadCastMessageRefresh((int) ((float) (maxPendingRequests - pendingRequests) / (float) maxPendingRequests * 100f));
            }
        }

    }

    private synchronized void removeRequest() {
        pendingRequests--;
    }


    public static boolean DEBUG = true;

    /**
     * Partiendo de un fichero JSON generar la estructura recursiva de Categorias, Subcategorias, Sub-Subcategorias, ...
     * hasta llegar al módulo raíz con los Quizzes
     *
     * @param mJSON
     * @throws IOException
     * @throws JSONException
     */
    public List<CategoryJSON> readCategoriesFromJSON(JSONObject mJSON) throws IOException, JSONException, URISyntaxException {

        List<CategoryJSON> mCategories = new ArrayList<>();
        JSONObject root;

        if (DEBUG) {
            String line;
            StringBuilder categoriesJson = new StringBuilder();
            InputStream rawCategories = mContext.getResources().openRawResource(R.raw.trivialandroidv4);
            BufferedReader reader = new BufferedReader(new InputStreamReader(rawCategories));

            // Crear una cadena con el Fichero JSON completo
            while ((line = reader.readLine()) != null) {
                categoriesJson.append(line);
            }
            rawCategories.close();
            reader.close();
            // Recorremos el JSON
            String categoriesStr = categoriesJson.toString();
            // Raiz principal
            root = new JSONObject(categoriesStr);

        } else {
            root = mJSON;
        }

        // Objeto categories
        JSONObject categories = root.getJSONObject("categories");

        // Genera tantas categorías como keys distintos tiene el JSON
        JSONObject category;
        CategoryJSON mCategory;
        Iterator<String> keys = categories.keys();
        while (keys.hasNext()) {
            // Note that "key" must be "1", "2", "3"...
            String key = keys.next();

            mCategory = new CategoryJSON();
            category = (JSONObject) categories.get(key);

            //mCategory.setId(category.getString("id"));
            mCategory.setCategory(category.getString("category"));

            mCategory.setDescription(category.getString("description"));
            mCategory.setImg(category.getString("img"));
            mCategory.setMoreinfo(category.getString("moreinfo"));
            mCategory.setTheme(category.getString("theme"));

            // Genera las subcategorías recursivamente
            if (category.has("subcategories")) {
                mCategory.setSubcategories(asignaSubtemas(category.getJSONObject("subcategories")));
            } else {
                mCategory.setSubcategories(null);
            }

            // Una categoría principal no debería tener Quizzes
            if (category.has("quizzes")) {
                // Quizzes
                JSONArray preguntasJSon = category.getJSONArray("quizzes");

                List<String> quizzies = new ArrayList<>();

                List<CategoryJSON> sub_subcategories = new ArrayList<>();
                CategoryJSON sub_subcategory = null;

                for (int j = 0; j < preguntasJSon.length(); j++) {
                    sub_subcategory = new CategoryJSON();
                    //sub_subcategory.setCategory(subcategory.getString("category"));
                    //sub_subcategory.setDescription(subcategory.getString("description"));
                    sub_subcategory.setImg(category.getString("img"));
                    //sub_subcategory.setMoreinfo(subcategory.getString("moreinfo"));
                    sub_subcategory.setTheme(category.getString("theme"));
                    // Añade manualmente las subtcategorias de las preguntas

                    quizzies.add((String) preguntasJSon.get(j));

                    sub_subcategories.add(sub_subcategory);

                    getQuizzesTXTFromInternetVolley(sub_subcategory, (String) preguntasJSon.get(j));
//                    localizaPreguntasTXT(sub_subcategory, (String) preguntasJSon.get(j));
                }
                mCategory.setSubcategories(sub_subcategories);
                // Los QUIZZIES se asignan mediente peticiones asíncronas
                ///mCategory.setQuizzes(quizzies);
            } else {
                mCategory.setQuizzes(null);
            }
            mCategories.add(mCategory);
        }
        return mCategories;
    }

    private List<CategoryJSON> asignaSubtemas(JSONObject subcategorias) throws JSONException, MalformedURLException, URISyntaxException {

        JSONObject subcategory;
        List<CategoryJSON> preguntas = new ArrayList<>();
        CategoryJSON pregunta = null;

        Iterator<String> keys = subcategorias.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            pregunta = new CategoryJSON();

            subcategory = (JSONObject) subcategorias.get(key);

            /// pregunta.setId(subcategory.getString("id"));
            pregunta.setCategory(subcategory.getString("category"));

            pregunta.setDescription(subcategory.getString("description"));
            pregunta.setImg(subcategory.getString("img"));
            pregunta.setMoreinfo(subcategory.getString("moreinfo"));
            pregunta.setTheme(subcategory.getString("theme"));

            if (subcategory.has("subcategories")) {
                pregunta.setQuizzes(null);
                pregunta.setSubcategories(asignaSubtemas(subcategory.getJSONObject("subcategories")));
            } else {
                pregunta.setSubcategories(null);

                if (subcategory.has("quizzes")) {
                    // Quizzes
                    JSONArray preguntasJSon = subcategory.getJSONArray("quizzes");

                    List<CategoryJSON> sub_subcategories = new ArrayList<>();
                    CategoryJSON sub_subcategory = null;

                    for (int j = 0; j < preguntasJSon.length(); j++) {

                        sub_subcategory = new CategoryJSON();
                        //sub_subcategory.setCategory(subcategory.getString("category"));

                        //sub_subcategory.setDescription(subcategory.getString("description"));
                        sub_subcategory.setImg(subcategory.getString("img"));
                        //sub_subcategory.setMoreinfo(subcategory.getString("moreinfo"));
                        sub_subcategory.setTheme(subcategory.getString("theme"));
                        // Añade manualmente las subtcategorias de las preguntas

                        sub_subcategories.add(sub_subcategory);

                        getQuizzesTXTFromInternetVolley(sub_subcategory, (String) preguntasJSon.get(j));
//                        localizaPreguntasTXT(sub_subcategory, (String) preguntasJSon.get(j));
                    }
                    pregunta.setSubcategories(sub_subcategories);
                    // Los QUIZZIES se asignan mediente peticiones asíncronas
                } else {
                    pregunta.setQuizzes(null);
                }
            }
            preguntas.add(pregunta);
        }
        return preguntas;
    }

    /**
     * Get Quizzes from a plain text in Internet
     *
     * @param sub_subcategory
     * @param urlStr
     */
    private void getQuizzesTXTFromInternetVolley(final CategoryJSON sub_subcategory, final String urlStr) throws MalformedURLException, URISyntaxException {
//        StringRequestHeaders request = new StringRequestHeaders(Request.Method.GET, "http://mmoviles.upv.es/test/OpenCV/3.2_OpenCV-Segmentacion.txt".replace(" ", "%20"), new Response.Listener<String>() {
        StringRequestHeaders request = new StringRequestHeaders(Request.Method.GET, urlStr.replace(" ", "%20"), new Response.Listener<String>() {
            @Override
            public void onResponse(final String response) {

                new Thread() {
                    public void run() {

                        try {
                            getQuizzesFromString(sub_subcategory, response, urlStr.replace(" ", "%20"));
//                            getQuizzesFromString(sub_subcategory, response, "http://mmoviles.upv.es/test/OpenCV/3.2_OpenCV-Segmentacion.txt".replace(" ", "%20"));
                            updateProgress();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            TopekaJSonHelper.getInstance(mContext, false).sendBroadCastMessage("ERROR");
                        }

                    }
                }.start();
            }
        }, new Response.ErrorListener()

        {
            @Override
            public void onErrorResponse(VolleyError error) {
                TopekaJSonHelper.getInstance(mContext, false).sendBroadCastError("Volley", "Loading quizzes!");

                TopekaJSonHelper.cancelRequests();
            }
        }, true);

        VolleySingleton.getInstance(mContext).addToRequestQueue(request);

        addRequest();
    }

}