import argparse
import os
from glob import glob

import cv2
import torch
import torch.backends.cudnn as cudnn
import yaml
from albumentations.augmentations import transforms
import albumentations as A
from albumentations.core.composition import Compose
from sklearn.model_selection import train_test_split
from tqdm import tqdm

import archs
from dataset import Dataset
from metrics import iou_score,pixel_acc
from utils import AverageMeter


def parse_args():
    parser = argparse.ArgumentParser()

    parser.add_argument('--name', default=None,
                        help='model name')

    args = parser.parse_args()

    return args


def main():
    args = parse_args()

    with open('models/%s/config.yml' % args.name, 'r') as f:
        config = yaml.load(f, Loader=yaml.FullLoader)

    print('-'*20)
    for key in config.keys():
        print('%s: %s' % (key, str(config[key])))
    print('-'*20)

    cudnn.benchmark = True

    # create model
    print("=> creating model %s" % config['arch'])

    #UNet and UNet++
    # model = archs.__dict__[config['arch']](config['num_classes'],
    #                                       config['input_channels'],
    #                                       config['deep_supervision'])
    
    #AttentionUNet
    # model = archs.__dict__[config['arch']](config['input_channels'],1)
    
    #DepthAttUNet
    model = archs.__dict__[config['arch']](config['input_channels'],1,1)
    model = model.cuda()

    # Data loading code
    img_ids = glob(os.path.join('inputs', config['dataset'], 'images', '*' + config['img_ext']))
    img_ids = [os.path.splitext(os.path.basename(p))[0] for p in img_ids]

    _, val_img_ids = train_test_split(img_ids, test_size=0.1, random_state=41)

    model.load_state_dict(torch.load('models/%s/model.pth' %
                                     config['name']))
    model.eval()

    # val_transform = A.Compose([
    #     # A.Resize(config['input_h'], config['input_w']),
    #     transforms.Normalize(),
    # ])

    val_dataset = Dataset(
        img_ids=val_img_ids,
        img_dir=os.path.join('inputs', config['dataset'], 'images'),
        depth_dir=os.path.join('inputs',config['dataset'],'norm_depth'), #new
        mask_dir=os.path.join('inputs', config['dataset'], 'masks'),
        img_ext=config['img_ext'],
        depth_ext=config['depth_ext'], #new
        mask_ext=config['mask_ext'],
        num_classes=config['num_classes'],
        transform=None)
    val_loader = torch.utils.data.DataLoader(
        val_dataset,
        batch_size=config['batch_size'],
        shuffle=False,
        # num_workers=config['num_workers'],
        num_workers=2,
        drop_last=False)

    # avg_meter = AverageMeter()
    avg_meters = {'iou': AverageMeter(),
                    'acc': AverageMeter()
    }

    for c in range(config['num_classes']):
        os.makedirs(os.path.join('outputs', config['name'], str(c)), exist_ok=True)
    with torch.no_grad():
        for input, depth, target, meta in tqdm(val_loader, total=len(val_loader)): #add depth variable
            input = input.cuda()
            depth=depth.cuda() #new
            target = target.cuda()

            # compute output
            if config['deep_supervision']:
                output = model(input)[-1]
            else:
                #Other UNet
                # output = model(input)
                #DepthAttUnet
                output = model(input,depth)

            iou = iou_score(output, target)
            acc = pixel_acc(output, target)
            # avg_meter.update(iou, input.size(0))
            avg_meters['iou'].update(iou, input.size(0))
            avg_meters['acc'].update(acc, input.size(0))

            output = torch.sigmoid(output).cpu().numpy()

            for i in range(len(output)):
                for c in range(config['num_classes']):
                    cv2.imwrite(os.path.join('outputs', config['name'], str(c), meta['img_id'][i] + '.png'),
                                (output[i, c] * 255).astype('uint8'))

    print('IoU: %.4f, Pixel Accuracy: %.4f' % (avg_meters['iou'].avg,avg_meters['acc'].avg))

    torch.cuda.empty_cache()


if __name__ == '__main__':
    main()
