package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Photo;
import com.main.suwoninfo.domain.Post;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.PhotoErrorCode;
import com.main.suwoninfo.exception.PostErrorCode;
import com.main.suwoninfo.dto.PhotoResponse;
import com.main.suwoninfo.idempotent.Idempotent;
import com.main.suwoninfo.repository.PhotoRepository;
import com.main.suwoninfo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.main.suwoninfo.utils.ToUtils.toPhotoResponse;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PostRepository postRepository;
    private final FileHandler fileHandler;

    @Transactional
    @Idempotent(key = "#postId")
    public Photo addPhoto(
            Photo photo,
            List<MultipartFile> files,
            Long postId) throws Exception {
        // 파일을 저장하고 그 Board 에 대한 list 를 가지고 있는다
        List<Photo> list = fileHandler.parseFileInfo(photo.getId(), files);
        Post findPost = postRepository.findById(postId).orElseThrow(()->  new CustomException(PostErrorCode.NOT_EXIST_POST));
        int listSize = list.size();

        if (list.isEmpty()){
            throw new CustomException(PhotoErrorCode.NOT_EXIST_PHOTO);
        }
        else if (list.size()>10) {
            throw new CustomException(PhotoErrorCode.OVER_IMAGE_SIZE);
        }
        // 파일에 대해 DB에 저장하고 가지고 있을 것
        else {
            findPost.addPhoto(list);
            for(int i=0; i<listSize; i++) {
                list.get(i).setPost(findPost);
                photoRepository.save(list.get(i));
            }
        }

        return photo;
    }

    public List<Photo> findAllByPost(Long postId) {

        return photoRepository.findByPost(postId);
    }

    public void photoDelete(Long photoId) {
        Photo findPhoto = photoRepository.findById(photoId).orElseThrow(()-> new CustomException(PhotoErrorCode.NOT_EXIST_PHOTO));
        photoRepository.delete(findPhoto);
    }

    @Transactional(readOnly = true)
    public PhotoResponse findByFileId(Long id) {

        Photo photo = photoRepository.findById(id).orElseThrow(() -> new CustomException(PhotoErrorCode.NOT_EXIST_PHOTO));
        return toPhotoResponse(photo);
    }
}
