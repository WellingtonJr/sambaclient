package com.wellington.sambaclient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jcifs.CIFSContext;
import jcifs.Credentials;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

@RestController
@RequestMapping("/documentos")
public class DocumentosController {

    @Value("${samba.server.url}")
    private String sambaServerUrl;

    @Value("${samba.server.username}")
    private String sambaUsername;

    @Value("${samba.server.password}")
    private String sambaPassword;

    @PostMapping("/upload")
    public String uploadPDF(@RequestParam("pdfFile") MultipartFile pdfFile) {
        System.out.println(pdfFile.getOriginalFilename());
        // String username = "well";
        // String password = "wasd";
        // String sharedPath = "smb://192.168.10.17/documentos/" + nomeArquivo;
        String sambaPath = sambaServerUrl + pdfFile.getOriginalFilename();

        try {
            // Crie um arquivo temporário local para o PDF
            File tempFile = java.io.File.createTempFile("temp", ".pdf");
            pdfFile.transferTo(tempFile);

            // Crie um SmbFile para o servidor Samba
            SingletonContext baseContext = SingletonContext.getInstance();
            Credentials credentials = new NtlmPasswordAuthenticator(null, sambaUsername, sambaPassword);
            CIFSContext testCtx = baseContext.withCredentials(credentials);
            SmbFile smbFile = new SmbFile(sambaPath, testCtx);

            // Abra um fluxo de saída para o SmbFile
            SmbFileOutputStream outputStream = new SmbFileOutputStream(smbFile);

            // Leia o arquivo temporário local e escreva-o no SmbFile
            try (FileInputStream localInputStream = new FileInputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = localInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            outputStream.close();
            tempFile.delete(); // Exclua o arquivo temporário local

            return "Arquivo PDF enviado com sucesso!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro ao enviar o arquivo PDF";
        }
    }

    @GetMapping("/download/{nomeArquivo}")
    public ResponseEntity<Object> baixarDocumento(@PathVariable String nomeArquivo) throws IOException {

        String sharedPath = sambaServerUrl + nomeArquivo;

        if(!fileExistsOnSamba(sharedPath, sambaUsername, sambaPassword)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Leitura de arquivo
            SingletonContext baseContext = SingletonContext.getInstance();
            Credentials credentials = new NtlmPasswordAuthenticator(null, sambaUsername, sambaPassword);
            CIFSContext testCtx = baseContext.withCredentials(credentials);
            SmbFile smbFileRead = new SmbFile(sharedPath, testCtx);
            SmbFileInputStream inputStream = new SmbFileInputStream(smbFileRead);

            InputStream is = new BufferedInputStream(inputStream);
            byte[] bytes = inputStreamToByteArray(is);

            inputStream.close();
           
            if (bytes != null) {
                String pdfBase64 = Base64.getEncoder().encodeToString(bytes);
                return ResponseEntity.ok(pdfBase64);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.notFound().build();
    }

    public static boolean fileExistsOnSamba(String smbFilePath, String username, String password) {
        try {
            SingletonContext baseContext = SingletonContext.getInstance();
            Credentials credentials = new NtlmPasswordAuthenticator(null, username, password);
            CIFSContext testCtx = baseContext.withCredentials(credentials);
            SmbFile smbFile = new SmbFile(smbFilePath, testCtx);

            // Use o método exists() para verificar se o arquivo existe
            return smbFile.exists();
        } catch (Exception e) {
            e.printStackTrace();
            return false; // Tratamento de erro: o arquivo não existe ou ocorreu um erro durante a
                          // verificação
        }
    }

    public static byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; // You can adjust the buffer size as needed
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }

    public static byte[] smbFileToByteArray(SmbFile smbFile) throws Exception {
        try (SmbFileInputStream inputStream = new SmbFileInputStream(smbFile)) {
            byte[] buffer = new byte[(int) smbFile.length()]; // Tamanho do arquivo
            int bytesRead = inputStream.read(buffer);
            if (bytesRead == smbFile.length()) {
                return buffer;
            } else {
                throw new IllegalStateException("Não foi possível ler o arquivo completamente.");
            }
        }
    }

    // @GetMapping("/{nomeArquivo}")
    // public ResponseEntity<byte[]> lerDocumento(@PathVariable String nomeArquivo)
    // {
    // try {
    // String caminhoArquivo = sambaServerUrl + "/" + nomeArquivo;
    // System.out.println(caminhoArquivo);
    // SmbFile smbFile = new SmbFile("smb://192.168.10.17/documentos/5BBM.pdf",
    // new NtlmPasswordAuthentication("", sambaUsername, sambaPassword));

    // if (smbFile.exists()) {
    // try (InputStream inputStream = smbFile.getInputStream()) {
    // byte[] bytes = inputStream.readAllBytes();
    // return ResponseEntity.ok().header("Content-Disposition", "inline; filename="
    // + nomeArquivo)
    // .body(bytes);
    // }
    // } else {
    // return ResponseEntity.notFound().build();
    // }
    // } catch (IOException e) {
    // e.printStackTrace();
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    // }
    // }

    // @PostMapping("/upload")
    // public ResponseEntity<String> enviarDocumento(@RequestParam("arquivo")
    // MultipartFile arquivo) {
    // try {
    // String nomeArquivo = arquivo.getOriginalFilename();
    // String caminhoArquivo = sambaServerUrl + "/" + nomeArquivo;
    // SmbFile smbFile = new SmbFile(caminhoArquivo,
    // new NtlmPasswordAuthentication("", sambaUsername, sambaPassword));

    // try (OutputStream outputStream = smbFile.getOutputStream()) {
    // outputStream.write(arquivo.getBytes());
    // return ResponseEntity.ok("Arquivo enviado com sucesso!");
    // }
    // } catch (IOException e) {
    // e.printStackTrace();
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao
    // enviar o arquivo.");
    // }
    // }
}
