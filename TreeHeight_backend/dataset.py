import os

import cv2
import numpy as np
import torch
import torch.utils.data


class Dataset(torch.utils.data.Dataset):
    def __init__(self, img_ids, img_dir, depth_dir, mask_dir, img_ext, depth_ext, mask_ext, num_classes, transform=None):
        """
        Args:
            img_ids (list): Image ids.
            img_dir: Image file directory.
            depth_dir: Depth file directory.
            mask_dir: Mask file directory.
            img_ext (str): Image file extension.
            depth_ext (str): Depth file extension.
            mask_ext (str): Mask file extension.
            num_classes (int): Number of classes.
            transform (Compose, optional): Compose transforms of albumentations. Defaults to None.
        
        Note:
            Make sure to put the files as the following structure:
            <dataset name>
            ├── images
            |   ├── 0a7e06.jpg
            │   ├── 0aab0a.jpg
            │   ├── 0b1761.jpg
            │   ├── ...
            |
            └── masks
                ├── 0
                |   ├── 0a7e06.png
                |   ├── 0aab0a.png
                |   ├── 0b1761.png
                |   ├── ...
                |
                ├── 1
                |   ├── 0a7e06.png
                |   ├── 0aab0a.png
                |   ├── 0b1761.png
                |   ├── ...
                ...
        """
        self.img_ids = img_ids
        self.img_dir = img_dir
        self.depth_dir=depth_dir
        self.mask_dir = mask_dir
        self.img_ext = img_ext
        self.depth_ext=depth_ext
        self.mask_ext = mask_ext
        self.num_classes = num_classes
        self.transform = transform

    def __len__(self):
        return len(self.img_ids)

    def __getitem__(self, idx):
        img_id = self.img_ids[idx]
        
        img = cv2.imread(os.path.join(self.img_dir, img_id + self.img_ext))
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB) #new
        
        depth = cv2.imread(os.path.join(self.depth_dir, img_id + self.depth_ext),cv2.IMREAD_GRAYSCALE)[..., None]
        # print('depth shape:',depth.shape)

        mask = []
        for i in range(self.num_classes):
            mask.append(cv2.imread(os.path.join(self.mask_dir, str(i),
                        img_id + self.mask_ext), cv2.IMREAD_GRAYSCALE)[..., None]) #读入单通道灰度图
        mask = np.dstack(mask) #拼接不同类别的mask
        # print('mask shape:',mask.shape)
        
        masks=[depth,mask]

        if self.transform is not None:
            augmented = self.transform(image=img, masks=masks)
            img = augmented['image']
            masks = augmented['masks']
            depth=masks[0]
            mask=masks[1]
        
        img = img.astype('float32') / 255
        img = img.transpose(2, 0, 1)
        depth = depth.astype('float32') / 255
        depth = depth.transpose(2, 0, 1)
        mask = mask.astype('float32') / 255
        mask = mask.transpose(2, 0, 1)
        
        return img, depth, mask, {'img_id': img_id}
