package com.main.suwoninfo.controller;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.dto.PhotoDto;
import com.main.suwoninfo.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 이미지 관련 컨트롤러
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/photo")
public class PhotoController {

    private final PhotoService photoService;

    //이미지 단일 출력
    @CrossOrigin
    @GetMapping(value = "/{id}",
            produces = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE})
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) throws IOException {
        //파일 id를 통해 이미지 dto를 받아옴
        PhotoDto photoDto = photoService.findByFileId(id);

        //절대 경로를 설정
        String absolutePath
                = new File("").getAbsolutePath() + "/";
        String path = photoDto.getFilePath();

        //입력 스트림으로 절대 경로 + 파일경로 입력
        InputStream imageStream = new FileInputStream(absolutePath + path);
        byte[] imageByteArray = IOUtil.toByteArray(imageStream);
        imageStream.close();
        return new ResponseEntity<>(imageByteArray, HttpStatus.OK);
    }

    @DeleteMapping(value = "/delete/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {

        photoService.photoDelete(id);

        return ResponseEntity.status(HttpStatus.OK).body("삭제 완료");
    }

    /**
     * 구버전 이미지 업로드 분리버전(현재는 포스팅 메소드에 합쳐짐)
     */
    /*@PostMapping("/upload/{postId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> upload(@RequestParam("files") List<MultipartFile> files,
                                         @PathVariable Long postId
    ) throws Exception {
        photoService.addPhoto(Photo.builder()
                .build(), files, postId);

        return ResponseEntity.ok().build();
    }*/

}
