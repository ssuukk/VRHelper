package pl.qus.milkserver

import pl.qus.threading.StoppableRunnable
import pl.qus.vrhelper.Logger

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

import pl.qus.vrhelper.readConfig
import pl.qus.vrhelper.*

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 (partially 1.1) server in Java
 *
 * NanoHTTPD version 1.25, Copyright  2001,2005-2012 Jarno Elonen
 * (elonen@iki.fi, http://iki.fi/elonen/) and Copyright  2010 Konstantinos
 * Togias (info@ktogias.gr, http://ktogias.gr)
 */
class VirtualMilkServer {
    private val myTcpPort: Int
    private val serverName: String
    private val allowedDirs: Array<File>

    init {
        val conf = readConfig()

        myTcpPort = conf.port
        serverName = conf.server
        allowedDirs = conf.dirs
    }

    internal var serverRunnable: StoppableRunnable = object : StoppableRunnable() {
        override fun execute() {
            try {
                while (!isCancelled)
                    HTTPSession(myServerSocket!!.accept())
                Logger.d("[MilkServer] Exiting http demon loop")
            } catch (ioe: IOException) {
            }
        }
    }

    private var myServerSocket: ServerSocket? = null

    fun startService() {
        Logger.d("[MilkServer] Trying to start HTTP daemon")

        try {
            myServerSocket = ServerSocket(myTcpPort)

            Thread(serverRunnable).start()

            Logger.d("[MilkServer] http daemon conjured!")
        } catch (ioe: IOException) {
            Logger.d("[MilkServer] Couldn't start samba streaming server", ioe)
        }

    }

    fun stopService() {
        try {
            myServerSocket?.close()
        } catch (e: IOException) {
        }

        serverRunnable.cancel()

        Logger.d("[MilkServer] http deamon exorcised!")
    }

    //========================================================================================
    // the real server
    //========================================================================================

    /**
     * Override this to customize the server.
     *
     *
     *
     *
     * (By default, this delegates to serveFile() and allows directory listing.)

     * @param uri    Percent-decoded URI without parameters, for example
     * *               "/index.cgi"
     * *
     * @param method "GET", "POST" etc.
     * *
     * @param parms  Parsed, percent decoded parameters from URI and, in case of
     * *               POST, data.
     * *
     * @param header Header entries, percent decoded
     * *
     * @return HTTP response, see class Response for details
     */
    fun serve(uri: String, method: String, header: Properties,
              parms: Properties, files: Properties): Response {
        Logger.d("$method '$uri' ")

        var e = header.propertyNames()
        while (e.hasMoreElements()) {
            val value = e.nextElement() as String
            Logger.d("  HDR: '" + value + "' = '" + header.getProperty(value)
                    + "'")
        }
        e = parms.propertyNames()
        while (e.hasMoreElements()) {
            val value = e.nextElement() as String
            Logger.d("  PRM: '" + value + "' = '" + parms.getProperty(value)
                    + "'")
        }
        e = files.propertyNames()
        while (e.hasMoreElements()) {
            val value = e.nextElement() as String
            Logger.d("  UPLOADED: '" + value + "' = '"
                    + files.getProperty(value) + "'")
        }

        return decideWhatToServe(uri, header)
    }

    internal fun decideWhatToServe(uri: String, header: Properties): Response {
        var res: Response? = null

        val noSlash = uri.substring(1)

        if (uri == "/" || uri.isEmpty()) {
            Logger.d("[MilkServer] serving index")
            res = serveIndex()
        } else if (uri.startsWith("/mvrl/")) {
            Logger.d("[MilkServer] serving mvrl")
            val pa = uri.substring(6)
            val msg = buildMvrlFile(pa)

            val plik = File(pa.substring(thisServerAddress.length))

            val r = Response(HTTP_OK, MIME_DEFAULT_BINARY, msg)

            r.addHeader("Content-Disposition", "inline; filename=\"" + plik.name + ".mvrl\"")

            return r
        } else if (isInsideAllowedDir(noSlash)) {
            Logger.d("[MilkServer] serving from allowed dir")
            res = serveFileOrDir(noSlash, header)
        } else {
            val current = System.getProperty("user.dir")
            val here = File(current, uri)

            if (here.exists()) {
                Logger.d("[MilkServer] serving home file:" + here.path)
                res = serveFile(here, header)
            } else if (uri.endsWith("/mainstyles.css")) {
                Logger.d("[MilkServer] serving CSS file:" + uri)
                res = serveCss(header)
            }
        }

        if (res == null) {
            Logger.d("[MilkServer] couldn't serve:" + uri)
            res = Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
                    "INTERNAL ERRROR: hey! How could you try and hack poor XenoAmp?!")
        }

        // Announce that the file
        // server accepts partial
        // content requestes
        res.addHeader("Accept-Ranges", "bytes")

        return res
    }

    private fun serveFileOrDir(uri: String, header: Properties): Response {
        val asFile = File(uri)

        if (asFile.isDirectory) {
            return serveDirectoryPage(uri, header)
        } else
        /*if (isVideo(asFile))*/ {
            return serveFile(asFile, header)
        }/* else
            return null;*/
    }


    private fun serveDirectoryPage(uri: String, header: Properties): Response {
        val scie = File(uri)

        try {
            if (scie.isDirectory) {
                Logger.d("[MilkServer] directory requested:" + scie.path)

                val pliki = scie.listFiles{ file -> isVideo(file) || file.isDirectory}

                Arrays.sort(pliki!!) { o1, o2 ->
                    if (o1.isDirectory && !o2.isDirectory)
                        -1
                    else if (!o1.isDirectory && o2.isDirectory)
                        1
                    else
                        o1.name.compareTo(o2.name, ignoreCase = true)
                }

                return Response(HTTP_OK, MIME_HTML, buildDirPage(scie, pliki))
            } else {
                return Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
                        "INTERNAL ERRROR: hey! How could you try and hack poor XenoAmp?!")
            }
        } catch (e: Exception) {
            return Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
                    "INTERNAL ERRROR: hey! How could you try and hack poor XenoAmp?!")
        }

    }

    private fun serveFile(scie: File, header: Properties): Response {
        var res: Response

        try {
            if (!scie.exists())
                return Response(HTTP_NOTFOUND, MIME_PLAINTEXT,
                        "Error 404, file not found.")


            Logger.d("[MilkServer] Succesfully opened:" + scie.path)
            // Get MIME type from file name extension, if possible
            var mime: String? = null

            val extension = scie.extension

            if (!extension.isEmpty())
                mime = theMimeTypes[extension.toLowerCase()] as String
            if (mime == null)
                mime = MIME_DEFAULT_BINARY

            // Calculate etag
            val etag = Integer.toHexString(scie.path.hashCode())

            // Support (simple) skipping:
            var startFrom: Long = 0
            var endAt: Long = -1
            var range: String? = header.getProperty("range")
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length)
                    val minus = range.indexOf('-')
                    try {
                        if (minus > 0) {
                            startFrom = java.lang.Long.parseLong(range.substring(0,
                                    minus))
                            endAt = java.lang.Long.parseLong(range
                                    .substring(minus + 1))
                        }
                    } catch (nfe: NumberFormatException) {
                    }

                }
            }

            // Change return code and add Content-Range header when skipping
            // is requested
            val fileLen = scie.length()
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = Response(HTTP_RANGE_NOT_SATISFIABLE,
                            MIME_PLAINTEXT, "")
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen)
                    res.addHeader("ETag", etag)
                } else {
                    if (endAt < 0)
                        endAt = fileLen - 1
                    var newLen = endAt - startFrom + 1
                    if (newLen < 0)
                        newLen = 0

                    val dataLen = newLen
                    // InputStream fis = scie.getInputStream();

                    val fis = FileInputStream(scie)

                    fis.skip(startFrom)

                    res = Response(HTTP_PARTIALCONTENT, mime, fis)
                    res.addHeader("Content-Length", "" + dataLen)
                    res.addHeader("Content-Range", "bytes " + startFrom
                            + "-" + endAt + "/" + fileLen)
                    res.addHeader("ETag", etag)
                }
            } else {
                if (etag == header.getProperty("if-none-match"))
                    res = Response(HTTP_NOTMODIFIED, mime, "")
                else {
                    res = Response(HTTP_OK, mime,
                            FileInputStream(scie))
                    res.addHeader("Content-Length", "" + fileLen)
                    res.addHeader("ETag", etag)
                }
            }
        } catch (ioe: IOException) {
            res = Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
                    "FORBIDDEN: Reading file failed.")
        }

        return res
    }

    /**
     * HTTP response. Return one of these from serve().
     */
    inner class Response(val status: String = HTTP_OK, val mimeType: String = "", val data: InputStream) {
        constructor(status: String, mimeType: String, txt: String) : this(status, mimeType, ByteArrayInputStream(txt.toByteArray(charset("UTF-8"))))

        var header = Properties()

        fun addHeader(name: String, value: String) {
            header.put(name, value)
        }
    }

    /**
     * Handles one session, i.e. parses the HTTP request and returns the
     * response.
     */
    private inner class HTTPSession(private val mySocket: Socket) : StoppableRunnable() {
        init {
            Thread(this).start()
        }

        override fun execute() {
            try {
                val istream = mySocket.getInputStream() ?: return

                // Read the first 8192 bytes.
                // The full header should fit in here.
                // Apache's default header limit is 8KB.
                // Do NOT assume that a single read will get the entire header
                // at once!
                val bufsize = 8192
                var buf = ByteArray(bufsize)
                var splitbyte = 0
                var rlen = 0
                run {
                    var read = istream.read(buf, 0, bufsize)
                    while (read > 0) {
                        rlen += read
                        splitbyte = findHeaderEnd(buf, rlen)
                        if (splitbyte > 0)
                            break
                        read = istream.read(buf, rlen, bufsize - rlen)
                    }
                }

                // Create a BufferedReader for parsing the header.
                val hbis = ByteArrayInputStream(buf, 0, rlen)
                val hin = BufferedReader(InputStreamReader(hbis))
                val pre = Properties()
                val parms = Properties()
                val header = Properties()
                val files = Properties()

                // Decode the header into parms and header java properties
                decodeHeader(hin, pre, parms, header)
                val method = pre.getProperty("method")
                if (method == null) {
                    Logger.d("[MilkServer] unknown method!")
                    // in.close();
                    istream.close()
                    return
                }
                val uri = pre.getProperty("uri")

                var size = 0x7FFFFFFFFFFFFFFFL
                val contentLength = header.getProperty("content-length")
                if (contentLength != null) {
                    try {
                        size = Integer.parseInt(contentLength).toLong()
                    } catch (ex: NumberFormatException) {
                    }

                }

                // Write the part of body already read to ByteArrayOutputStream
                // f
                val f = ByteArrayOutputStream()
                if (splitbyte < rlen)
                    f.write(buf, splitbyte, rlen - splitbyte)

                // While Firefox sends on the first read all the data fitting
                // our buffer, Chrome and Opera send only the headers even if
                // there is data for the body. We do some magic here to findAlbums
                // out whether we have already consumed part of body, if we
                // have reached the end of the data to be sent or we should
                // expect the first byte of the body at the next read.
                if (splitbyte < rlen)
                    size -= (rlen - splitbyte + 1).toLong()
                else if (splitbyte == 0 || size == 0x7FFFFFFFFFFFFFFFL)
                    size = 0

                // Now read all the body and write it to f
                buf = ByteArray(512)
                while (rlen >= 0 && size > 0) {
                    rlen = istream.read(buf, 0, 512)
                    size -= rlen.toLong()
                    if (rlen > 0)
                        f.write(buf, 0, rlen)
                }

                // Get the raw body as a byte []
                val fbuf = f.toByteArray()

                // Create a BufferedReader for easily reading it as string.
                val bin = ByteArrayInputStream(fbuf)
                val insputStream = BufferedReader(InputStreamReader(
                        bin))

                // If the method is POST, there may be parameters
                // in data section, too, read it:
                if (method.equals("POST", ignoreCase = true)) {
                    var contentType = ""
                    val contentTypeHeader = header
                            .getProperty("content-type")
                    var st = StringTokenizer(contentTypeHeader,
                            "; ")
                    if (st.hasMoreTokens()) {
                        contentType = st.nextToken()
                    }

                    if (contentType.equals("multipart/form-data", ignoreCase = true)) {
                        // Handle multipart/form-data
                        if (!st.hasMoreTokens())
                            sendError(
                                    HTTP_BADREQUEST,
                                    "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html")
                        val boundaryExp = st.nextToken()
                        st = StringTokenizer(boundaryExp, "=")
                        if (st.countTokens() != 2)
                            sendError(
                                    HTTP_BADREQUEST,
                                    "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html")
                        st.nextToken()
                        val boundary = st.nextToken()

                        decodeMultipartData(boundary, fbuf, insputStream, parms, files)
                    } else {
                        // Handle application/x-www-form-urlencoded
                        var postLine = ""
                        val pbuf = CharArray(512)
                        var read = insputStream.read(pbuf)
                        while (read >= 0 && !postLine.endsWith("\r\n")) {
                            postLine += String(pbuf, 0, read)
                            read = insputStream.read(pbuf)
                        }
                        postLine = postLine.trim { it <= ' ' }
                        decodeParms(postLine, parms)
                    }
                }

                if (method.equals("PUT", ignoreCase = true))
                    files.put("content", saveTmpFile(fbuf, 0, f.size()))

                // Ok, now do the serve()
                val r = serve(uri, method, header, parms, files)
                sendResponse(r.status, r.mimeType, r.header, r.data)

                insputStream.close()
                istream.close()
            } catch (ioe: IOException) {
                try {
                    sendError(
                            HTTP_INTERNALERROR,
                            "SERVER INTERNAL ERROR: IOException: " + ioe.message)
                } catch (t: Throwable) {
                }

            } catch (ie: InterruptedException) {
                // Thrown by sendError, ignore and exit the thread.
            }

        }

        /**
         * Decodes the sent headers and loads the data into java Properties' key
         * - value pairs
         */
        private fun decodeHeader(`in`: BufferedReader, pre: Properties, parms: Properties, header: Properties) {
            try {
                // Read the request line
                val inLine = `in`.readLine() ?: return

                Logger.d("[MilkServer] incoming:" + inLine)

                val st = StringTokenizer(inLine)
                if (!st.hasMoreTokens())
                    sendError(HTTP_BADREQUEST,
                            "BAD REQUEST: Syntax error. Usage: GET /example/file.html")

                val method = st.nextToken()
                pre.put("method", method)

                if (!st.hasMoreTokens())
                    sendError(HTTP_BADREQUEST,
                            "BAD REQUEST: Missing URI. Usage: GET /example/file.html")

                var uri = st.nextToken()

                // Decode parameters from the URI
                val qmi = uri.indexOf('?')
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms)
                    uri = URLDecoder.decode(uri.substring(0, qmi), "UTF-8")
                } else
                    uri = URLDecoder.decode(uri, "UTF-8")

                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names lowercase since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    var line: String? = `in`.readLine()
                    while (line != null && line.trim { it <= ' ' }.isNotEmpty()) {
                        val p = line.indexOf(':')
                        if (p >= 0)
                            header.put(line.substring(0, p).trim { it <= ' ' }
                                    .toLowerCase(), line.substring(p + 1)
                                    .trim { it <= ' ' })
                        line = `in`.readLine()
                    }
                }

                pre.put("uri", uri)
            } catch (ioe: IOException) {
                sendError(
                        HTTP_INTERNALERROR,
                        "SERVER INTERNAL ERROR: IOException: " + ioe.message)
            }

        }

        /**
         * Decodes the Multipart Body data and put it into java Properties' key
         * - value pairs.
         */
        @Throws(InterruptedException::class)
        private fun decodeMultipartData(boundary: String, fbuf: ByteArray,
                                        `in`: BufferedReader, parms: Properties, files: Properties) {
            try {
                val bpositions = getBoundaryPositions(fbuf,
                        boundary.toByteArray())
                var boundarycount = 1
                var mpline: String? = `in`.readLine()
                while (mpline != null) {
                    if (mpline.indexOf(boundary) == -1)
                        sendError(
                                HTTP_BADREQUEST,
                                "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html")
                    boundarycount++
                    val item = Properties()
                    mpline = `in`.readLine()
                    while (mpline != null && mpline.trim { it <= ' ' }.isNotEmpty()) {
                        val p = mpline.indexOf(':')
                        if (p != -1)
                            item.put(mpline.substring(0, p).trim { it <= ' ' }
                                    .toLowerCase(), mpline.substring(p + 1)
                                    .trim { it <= ' ' })
                        mpline = `in`.readLine()
                    }
                    if (mpline != null) {
                        val contentDisposition = item
                                .getProperty("content-disposition")
                        if (contentDisposition == null) {
                            sendError(
                                    HTTP_BADREQUEST,
                                    "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html")
                        }
                        val st = StringTokenizer(
                                contentDisposition!!, "; ")
                        val disposition = Properties()
                        while (st.hasMoreTokens()) {
                            val token = st.nextToken()
                            val p = token.indexOf('=')
                            if (p != -1)
                                disposition.put(token.substring(0, p).trim { it <= ' ' }
                                        .toLowerCase(), token.substring(p + 1)
                                        .trim { it <= ' ' })
                        }
                        var pname = disposition.getProperty("name")
                        pname = pname.substring(1, pname.length - 1)

                        var value = ""
                        if (item.getProperty("content-type") == null) {
                            while (mpline != null && mpline.indexOf(boundary) == -1) {
                                mpline = `in`.readLine()
                                if (mpline != null) {
                                    val d = mpline.indexOf(boundary)
                                    if (d == -1)
                                        value += mpline
                                    else
                                        value += mpline.substring(0, d - 2)
                                }
                            }
                        } else {
                            if (boundarycount > bpositions.size)
                                sendError(HTTP_INTERNALERROR,
                                        "Error processing request")
                            val offset = stripMultipartHeaders(fbuf,
                                    bpositions[boundarycount - 2])
                            val path = saveTmpFile(fbuf, offset,
                                    bpositions[boundarycount - 1] - offset - 4)
                            files.put(pname, path)
                            value = disposition.getProperty("filename")
                            value = value.substring(1, value.length - 1)
                            do {
                                mpline = `in`.readLine()
                            } while (mpline != null && mpline.indexOf(boundary) == -1)
                        }
                        parms.put(pname, value)
                    }
                }
            } catch (ioe: IOException) {
                sendError(
                        HTTP_INTERNALERROR,
                        "SERVER INTERNAL ERROR: IOException: " + ioe.message)
            }

        }

        /**
         * Find byte index separating header from body. It must be the last byte
         * of the first two sequential new lines.
         */
        private fun findHeaderEnd(buf: ByteArray, rlen: Int): Int {
            var splitbyte = 0
            while (splitbyte + 3 < rlen) {
                if (buf[splitbyte].toChar() == '\r' && buf[splitbyte + 1].toChar() == '\n'
                        && buf[splitbyte + 2].toChar() == '\r'
                        && buf[splitbyte + 3].toChar() == '\n')
                    return splitbyte + 4
                splitbyte++
            }
            return 0
        }

        /**
         * Find the byte positions where multipart boundaries start.
         */
        fun getBoundaryPositions(b: ByteArray, boundary: ByteArray): IntArray {
            var matchcount = 0
            var matchbyte = -1
            val matchbytes = Vector<Int>()
            run {
                var i = 0
                while (i < b.size) {
                    if (b[i] == boundary[matchcount]) {
                        if (matchcount == 0)
                            matchbyte = i
                        matchcount++
                        if (matchcount == boundary.size) {
                            matchbytes.addElement(matchbyte)
                            matchcount = 0
                            matchbyte = -1
                        }
                    } else {
                        i -= matchcount
                        matchcount = 0
                        matchbyte = -1
                    }
                    i++
                }
            }
            val ret = IntArray(matchbytes.size)
            for (i in ret.indices) {
                ret[i] = (matchbytes.elementAt(i) as Int).toInt()
            }
            return ret
        }

        /**
         * Retrieves the content of a sent file and saves it to a temporary
         * file. The full path to the saved file is returned.
         */
        private fun saveTmpFile(b: ByteArray, offset: Int, len: Int): String {
            var path = ""
            if (len > 0) {
                val tmpdir = System.getProperty("java.io.tmpdir")
                try {
                    val temp = File.createTempFile("NanoHTTPD", "", File(
                            tmpdir))
                    val fstream = FileOutputStream(temp)
                    fstream.write(b, offset, len)
                    fstream.close()
                    path = temp.absolutePath
                } catch (e: Exception) { // Catch exception if any
                    Logger.d("[MilkServer] Error: " + e.message)
                }

            }
            return path
        }

        /**
         * It returns the offset separating multipart file headers from the
         * file's data.
         */
        private fun stripMultipartHeaders(b: ByteArray, offset: Int): Int {
            var i = offset
            while (i < b.size) {
                if (b[i].toChar() == '\r' && b[++i].toChar() == '\n' && b[++i].toChar() == '\r'
                        && b[++i].toChar() == '\n')
                    break
                i++
            }
            return i + 1
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Properties. NOTE: this doesn't support multiple identical keys due to
         * the simplicity of Properties -- if you need multiples, you might want
         * to replace the Properties with a Hashtable of Vectors or such.
         */
        private fun decodeParms(parms: String?, p: Properties) {
            if (parms == null)
                return

            val st = StringTokenizer(parms, "&")
            while (st.hasMoreTokens()) {
                val e = st.nextToken()
                val sep = e.indexOf('=')

                if (sep >= 0)
                    try {
                        p.put(URLDecoder.decode(e.substring(0, sep), "UTF-8").trim { it <= ' ' },
                                URLDecoder.decode(e.substring(sep + 1), "UTF-8"))
                    } catch (e1: UnsupportedEncodingException) {
                        Logger.d("[MilkServer] Problem decoding params", e1)
                    }

            }
        }

        /**
         * Returns an error message as a HTTP response and throws
         * InterruptedException to stop further request processing.
         */
        private fun sendError(status: String, msg: String) {
            sendResponse(status, MIME_PLAINTEXT, null,
                    ByteArrayInputStream(generateErrorPage(status, msg)
                            .toByteArray()))
            throw InterruptedException()
        }

        // TODO: zrobić ładną stronę
        private fun generateErrorPage(status: String, msg: String): String {
            return msg
        }

        /**
         * Sends given response to the socket.
         */
        private fun sendResponse(status: String, mime: String?,
                                 header: Properties?, data: InputStream?) {
            try {
                val out = mySocket.getOutputStream()
                val pw = PrintWriter(out)
                pw.print("HTTP/1.0 $status \r\n")

                if (mime != null)
                    pw.print("Content-Type: " + mime + "\r\n")

                if (header == null || header.getProperty("Date") == null)
                    pw.print("Date: " + gmtFrmt.format(Date()) + "\r\n")

                if (header != null) {
                    val e = header.keys()
                    while (e.hasMoreElements()) {
                        val key = e.nextElement() as String
                        val value = header.getProperty(key)
                        pw.print(key + ": " + value + "\r\n")
                    }
                }

                pw.print("\r\n")
                pw.flush()

                data?.copyTo(out, theBufferSize)

                out.flush()
                out.close()
                data?.close()
            } catch (ioe: IOException) {
                try {
                    mySocket.close()
                } catch (t: Throwable) {
                }

            }

        }
    }

    //========================================================================================
    // serve various pre-cooked responses
    //========================================================================================

    private fun serveIndex(): Response {
        val msg = buildDirPage(File("/"), allowedDirs)
        return Response(HTTP_OK, MIME_HTML, msg)
    }

    private fun serveCss(header: Properties): Response {
        Logger.d("[MilkServer] serving css")

        val css = "body {background-color:#000000;}" +
                "a {text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-size:150%;}" +
                "a.guziki {text-align:center; text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-weight: bolder;font-size:180;text-shadow: black 0em 0em 0.2em;}" +
                "a.tytul {text-align:center; font-size:300%;text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-weight: bolder;text-shadow: black 0em 0em 0.2em;}" +
                "a.album {text-align:center; font-size:200%;text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-weight: bolder;text-shadow: black 0em 0em 0.2em;}" +
                "a.wykonawca {text-align:center; font-size:180%;text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-weight: bolder;text-shadow: black 0em 0em 0.2em;}" +
                "a.naglowek {text-align:center; font-size:300%;text-decoration:none; color:#ffffff; font-family:Arial,Helvetica,sans-serif;font-weight: bolder;text-shadow: black 0em 0em 0.2em;}" +
                "hr.niebieska {color:#31b6e7; background-color: #31b6e7; height: 4px;}" +
                "hr.divider {color:#888888; background-color: #888888; height: 2px;}" +
                "td.dirsmalline {font-size:130%; color:#ffffff; text-align:left;line-height:100%; font-family:Arial,Helvetica,sans-serif;}" +
                "td.dirbigline {font-size:130%; color:#ffffff; text-align:left;line-height:100%; font-family:Arial,Helvetica,sans-serif;font-weight: bolder }" +
                "td.sheeticlabel {color:#ffffff; text-align:center;font-family:Arial,Helvetica,sans-serif;}" +
                "td.sheetrowlabel {color:#ffffff; text-align:left;font-family:Arial,Helvetica,sans-serif;fon-weight: bolder;}"

        return Response(HTTP_OK, MIME_CSS, css)
    }

    private fun buildDirPage(path: File, files: Array<File>?): String {
        if (files == null)
            return "404 file not found"

        var msg = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\r\n<html>\r\n\r\n<head>\r\n<meta content=\"pl\" http-equiv=\"Content-Language\" />\r\n<meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\" />\r\n<style type=\"text/css\">\r\n.cover-style {\r\n\tborder-left-style: solid;\r\n\tborder-right-style: solid;\r\n}\r\n.current-dir-link-style {\r\n\tfont-family: 'Roboto', sans-serif;\r\n\tfont-size: xx-large;\r\n\tmargin-top: 00px;\r\n\tmargin-bottom: 0px;\r\n}\r\n.breadcrumbs-style {\r\n\tfont-family: 'Roboto', sans-serif;\r\n\tmargin-top: 0px;\r\n\tmargin-bottom: 10px;\r\n}\r\n.duration-style {\r\n\ttext-align: right;\r\n\tfont-family: 'Roboto', sans-serif;\r\n\tvertical-align: top;\r\n}\r\n.first-line-style {\r\n\tfont-family: Arial, Helvetica, sans-serif;\r\n\tfont-size: x-large;\r\n\tfont-style: normal;\r\n\tfont-weight: bold;\r\n\tpadding-left: 5px;\r\n\tvertical-align: bottom;\r\n\twhite-space: nowrap;\r\n\toverflow: hidden;\r\n\ttext-overflow: ellipsis;\r\n}\r\n.second-line-style {\r\n\tfont-family: Arial, Helvetica, sans-serif;\r\n\tpadding-left: 5px;\r\n\tvertical-align: top;\r\n\twhite-space: nowrap;\r\n\toverflow: hidden;\r\n\ttext-overflow: ellipsis;\r\n}\r\n.linia-style {\r\n\tcolor: #FFFFFF;\r\n}\r\n.track-link-style {\r\n\ttext-decoration: none;\r\n}\r\n</style>\r\n<link href=\"http://fonts.googleapis.com/css?family=Roboto\" rel=\"stylesheet\" type=\"text/css\" />\r\n</head>\r\n\r\n<body style=\"color: #FFFFFF; background-color: #000000\">\r\n\r\n"

        try {
            //msg += "<p class=\"current-dir-link-style\">\r\n<a class=\"track-link-style\" href=\"" + encodeUri(path.getParent()) + "\">\r\n<span class=\"linia-style\">Parent directory</span></a></p>\n";
            msg += "<p class=\"breadcrumbs-style\">" + path.name + "</p>"
        } catch (ex: Exception) {
        }

        msg += "<table align=\"center\" style=\"width: 600px\">\r\n"

        for (track in files) {
            val dir = track.isDirectory

            val pierwsza: String
            val druga: String
            val ikonaLink: String
            var nazwa = track.name

            if (nazwa == "")
                nazwa = "Root directory"

            if (dir) {
                val scie = encodeUri(track.path)
                pierwsza = "<a class=\"track-link-style\" href=\"/$scie\">"+
                "<span class=\"linia-style\">" + nazwa + "</span></a>"

                druga = "Folder"

                ikonaLink = "folder.png"
            } else {
                val scie = "milkvr://sideload/?url=" + encodeUri(thisServerAddress + "/" + track.path) + "&video_type=" + guessVideoType(track.path)
                val mvrl = "/mvrl/" + encodeUri(thisServerAddress + "/" + track.path)

                pierwsza = "<a class=\"track-link-style\" href=\"$scie\">" +
                        "<span class=\"linia-style\">" + nazwa + "</span></a>"

                val type = guessVideoType(track.path)

                val opis: String

                if (type == "_2dp")
                    opis = "Video File"
                else if (type.contains("3d"))
                    opis = "3D Video File"
                else
                    opis = "Panoramic Video File"

                druga = "<a class=\"track-link-style\" href=\"$mvrl\">"+
                "<span class=\"linia-style\">" + opis + "</span></a>"

                val thumb = findThumbnil(track.path)

                //getThisServerAddress()+"/"+encodeUri(thumb)

                if (thumb == null)
                    ikonaLink = "video.png"
                else
                    ikonaLink = thisServerAddress + "/" + encodeUri(thumb)
            }

            msg += "\t<tr>\r\n\t\t<td rowspan=\"2\" style=\"width: 100px; height: 100px\">" +
                    "\r\n\t\t<img alt=\"Cover\" height=\"100\" src=\"" + ikonaLink + "\" width=\"100\" /></td>" +
                    "\r\n\t\t<td class=\"first-line-style\" colspan=\"2\">" + pierwsza + "</td>\r\n\t</tr>\r\n\t" +
                    "<tr>" +
                    "\r\n\t\t<td class=\"second-line-style\">" + druga + "</td>" +
                    //                    + "\r\n\t\t<td class=\"duration-style\" style=\"width: 100px\">cos innego</td>"+
                    "\r\n\t</tr>" +
                    "\r\n"
        }

        msg += "</table></body></html>"

        return msg
    }

    private fun buildMvrlFile(path: String): String {
        val toEnc = path.substring(thisServerAddress.length + 1)
        var msg = thisServerAddress + "/" + encodeUri(toEnc) + "\n" +
                guessVideoType(path) + "\n" +
                guessAudioType(path) + "\n"

        val thumb = findThumbnil(path.substring(thisServerAddress.length + 1))

        if (thumb != null)
            msg += thisServerAddress + "/" + encodeUri(thumb) + "\n"

        return msg
    }

    //========================================================================================
    // some utility functions
    //========================================================================================

    /**
     * URL-encodes everything between "/"-characters. Encodes spaces as '%20'
     * instead of '+'.
     */

    private fun encodeUri(uri: String)=  try {
            URLEncoder.encode(uri, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            uri
        }

    internal fun isInsideAllowedDir(f: String) = allowedDirs.any { f.startsWith(it.path) }

    private val thisServerAddress: String
        get() = "http://$serverName:$myTcpPort"

    companion object {
        val HTTP_OK = "200 OK"
        val HTTP_PARTIALCONTENT = "206 Partial Content"
        val HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable"
        val HTTP_REDIRECT = "301 Moved Permanently"
        val HTTP_NOTMODIFIED = "304 Not Modified"
        val HTTP_FORBIDDEN = "403 Forbidden"
        val HTTP_NOTFOUND = "404 Not Found"
        val HTTP_BADREQUEST = "400 Bad Request"
        val HTTP_INTERNALERROR = "500 Internal Server Error"
        val HTTP_NOTIMPLEMENTED = "501 Not Implemented"
        /**
         * Common mime types for dynamic content
         */
        val MIME_PLAINTEXT = "text/plain"
        val MIME_HTML = "text/html"
        val MIME_DEFAULT_BINARY = "application/octet-stream"
        val MIME_XML = "text/xml"
        val MIME_CSS = "text/css"

        /**
         * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
         */
        private val theMimeTypes = Hashtable<String, String>()
        private val theBufferSize = 16 * 1024
        /**
         * GMT date formatter
         */
        private val gmtFrmt: java.text.SimpleDateFormat = java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)

        init {
            val st = StringTokenizer(
                    "css		text/css "
                            + "htm		text/html "
                            + "html		text/html "
                            + "xml		text/xml "
                            + "txt		text/plain "
                            + "asc		text/plain "
                            + "gif		image/gif "
                            + "jpg		image/jpeg "
                            + "jpeg		image/jpeg "
                            + "png		image/png "
                            + "mp3		audio/mpeg "
                            + "m3u		audio/mpeg-url "
                            + "mp4		video/mp4 "
                            + "ogv		video/ogg "
                            + "avi		video/x-msvideo "
                            + "mkv		video/x-matroska "
                            + "mk3d		video/x-matroska-3d "
                            + "wmv		video/x-ms-wmv "
                            + "flv		video/x-flv "
                            + "mov		video/quicktime "
                            + "mpg		video/mpeg "
                            + "mpeg		video/mpeg "
                            + "swf		application/x-shockwave-flash "
                            + "js			application/javascript "
                            + "pdf		application/pdf "
                            + "doc		application/msword "
                            + "ogg		application/x-ogg "
                            + "zip		application/octet-stream "
                            + "exe		application/octet-stream "
                            + "m3u8		audio/x-mpegurl "
                            + "m3u		audio/x-mpegurl "
                            + "class		application/octet-stream ")
            while (st.hasMoreTokens())
                theMimeTypes.put(st.nextToken(), st.nextToken())
        }

        init {
            gmtFrmt.timeZone = TimeZone.getTimeZone("GMT")
        }

        //========================================================================================
        // initialiation etc.
        //========================================================================================

        @Throws(IOException::class)
        @JvmStatic fun main(args: Array<String>) {
            val serv = VirtualMilkServer()
            serv.startService()

            Runtime.getRuntime().addShutdownHook(Thread {
                serv.stopService()
            })

            //System.`in`.read()

            do {
                Thread.sleep(Long.MAX_VALUE)
            } while (true)

            //serv.stopService()
        }
    }

}
